package vn.svframe.svframemmo.gui;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.gui.Navigator;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframelib.gui.editable.EditableInventory;
import vn.svframe.svframelib.gui.editable.GeneratedInventory;
import vn.svframe.svframelib.gui.editable.item.InventoryItem;
import vn.svframe.svframelib.gui.editable.item.PhysicalItem;
import vn.svframe.svframelib.gui.editable.item.SimpleItem;
import vn.svframe.svframelib.gui.editable.placeholder.Placeholders;
import vn.svframe.svframelib.manager.StatManager;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttribute;
import vn.svframe.svframemmo.experience.Booster;
import vn.svframe.svframemmo.experience.Profession;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlayerStats extends EditableInventory {
    public PlayerStats() { super("player-stats"); }

    @Override public InventoryItem<?> resolveItem(String function, Map<String, Object> config) {
        if (function.equals("boost")) return new BoostItem(config);
        if (function.equals("boost-next")) return new BoostNextButton(config);
        if (function.equals("boost-previous")) return new BoostPreviousButton(config);
        if (function.startsWith("profession_")) return new ProfessionItem(function, config);
        if (function.equals("profile")) return new PlayerProfileItem(config);
        if (function.equals("stats")) return new StatsItem(config);
        // Party/social GUI surface is intentionally excluded from the retained MMOCore scope.
        return null;
    }

    public PlayerStatsInventory newInventory(PlayerData target, PlayerData opening) { return new PlayerStatsInventory(target, opening); }
    public PlayerStatsInventory newInventory(PlayerData player) { return new PlayerStatsInventory(player, player); }

    private final class BoostPreviousButton extends SimpleItem<PlayerStatsInventory> {
        BoostPreviousButton(Map<String, ?> config) { super(config); }
        @Override public void onClick(PlayerStatsInventory inv, PluginInventory.Click click) { inv.boostOffset--; inv.open(); }
        @Override public boolean isDisplayed(PlayerStatsInventory inv) { return inv.boostOffset > 0; }
    }

    private final class BoostNextButton extends SimpleItem<PlayerStatsInventory> {
        BoostNextButton(Map<String, ?> config) { super(config); }
        @Override public boolean isDisplayed(PlayerStatsInventory inv) {
            InventoryItem<?> boost = inv.getByFunction("boost");
            return boost != null && inv.boostOffset + boost.getSlots().size() < SVFrameMMO.boosters().getActive().size();
        }
        @Override public void onClick(PlayerStatsInventory inv, PluginInventory.Click click) { inv.boostOffset++; inv.open(); }
    }

    private static final class ProfessionItem extends PhysicalItem<PlayerStatsInventory> {
        private final Profession profession;
        ProfessionItem(String function, Map<String, ?> config) {
            super(config);
            String id = function.substring("profession_".length()).toLowerCase(Locale.ROOT);
            profession = SVFrameMMO.professions().get(id);
            if (profession == null) throw new IllegalArgumentException("Unknown profession in GUI: " + id);
        }
        @Override public boolean hasDifferentDisplay() { return true; }
        @Override public Placeholders getPlaceholders(PlayerStatsInventory inv, int n) {
            double exp = inv.target.getProfessions().getExperience(profession);
            long next = inv.target.getProfessions().getLevelUpExperience(profession);
            return GuiSupport.placeholders(
                    "progress", GuiSupport.progress(exp, next),
                    "level", inv.target.getProfessions().getLevel(profession),
                    "xp", exp, "exp", exp,
                    "next_level", next,
                    "percent", GuiSupport.percent(exp, next),
                    "profession", profession.getName(),
                    "fishing_strength", StatManager.format("FISHING_STRENGTH", inv.target.getMMOPlayerData().getStatMap().getStat("FISHING_STRENGTH")),
                    "critical_fishing_chance", StatManager.format("CRITICAL_FISHING_CHANCE", inv.target.getMMOPlayerData().getStatMap().getStat("CRITICAL_FISHING_CHANCE")),
                    "critical_fishing_failure_chance", StatManager.format("CRITICAL_FISHING_FAILURE_CHANCE", inv.target.getMMOPlayerData().getStatMap().getStat("CRITICAL_FISHING_FAILURE_CHANCE")));
        }
    }

    private static final class StatsItem extends PhysicalItem<PlayerStatsInventory> {
        StatsItem(Map<String, ?> config) { super(config); }
        @Override public Placeholders getPlaceholders(PlayerStatsInventory inv, int n) {
            return new Placeholders() {
                @Override public String parsePlaceholder(String holder) {
                    if (holder == null) return "";
                    String lower = holder.toLowerCase(Locale.ROOT);
                    if (lower.endsWith("_base")) {
                        String stat = UtilityMethods.enumName(holder.substring(0, holder.length() - 5));
                        return StatManager.format(stat, inv.target.getMMOPlayerData().getStatMap().getInstance(stat).getBase());
                    }
                    if (lower.endsWith("_extra")) {
                        String stat = UtilityMethods.enumName(holder.substring(0, holder.length() - 6));
                        var instance = inv.target.getMMOPlayerData().getStatMap().getInstance(stat);
                        return StatManager.format(stat, instance.getTotal() - instance.getBase());
                    }
                    if (lower.startsWith("attribute_")) {
                        String attrId = holder.substring(10).replace('_', '-').toLowerCase(Locale.ROOT);
                        PlayerAttribute attr = SVFrameMMO.attributes().get(attrId);
                        return attr == null ? "0" : String.valueOf(inv.target.getAttributes().getAttribute(attr));
                    }
                    if (lower.equals("health")) return StatManager.format("MAX_HEALTH", inv.target.getHealth());
                    if (lower.equals("mana")) return StatManager.format("MAX_MANA", inv.target.getMana());
                    if (lower.equals("stamina")) return StatManager.format("MAX_STAMINA", inv.target.getStamina());
                    if (lower.equals("stellium")) return StatManager.format("MAX_STELLIUM", inv.target.getStellium());
                    String stat = UtilityMethods.enumName(holder);
                    return StatManager.format(stat, inv.target.getMMOPlayerData().getStatMap().getStat(stat));
                }
            };
        }
    }

    private static final class PlayerProfileItem extends PhysicalItem<PlayerStatsInventory> {
        PlayerProfileItem(Map<String, ?> config) { super(config); }
        @Override public Placeholders getPlaceholders(PlayerStatsInventory inv, int n) {
            PlayerData data = inv.target;
            long next = data.getLevelUpExperience();
            return GuiSupport.placeholders(
                    "percent", GuiSupport.percent(data.getExperience(), next),
                    "exp", data.getExperience(), "level", data.getLevel(),
                    "class_points", data.getClassPoints(), "skill_points", data.getSkillPoints(),
                    "attribute_points", data.getAttributePoints(),
                    "progress", GuiSupport.progress(data.getExperience(), next), "next_level", next,
                    "player", data.isOnline() ? data.getPlayer().getName().getString() : data.getUniqueId().toString(),
                    "class", data.getProfess().getName(),
                    "mana", data.getMana(), "stamina", data.getStamina(), "stellium", data.getStellium());
        }
    }

    private final class BoostItem extends SimpleItem<PlayerStatsInventory> {
        private final PhysicalItem<PlayerStatsInventory> noBoost;
        private final PhysicalItem<PlayerStatsInventory> mainLevel;
        private final PhysicalItem<PlayerStatsInventory> profession;
        private final String expired;

        BoostItem(Map<String, ?> config) {
            super(config);
            Map<String, Object> no = GuiSupport.map(config.get("no-boost"));
            Map<String, Object> main = GuiSupport.map(config.get("main-level"));
            Map<String, Object> prof = GuiSupport.map(config.get("profession"));
            if (no.isEmpty() || main.isEmpty() || prof.isEmpty()) throw new IllegalArgumentException("Boost GUI item requires no-boost/main-level/profession configs");
            expired = GuiSupport.string(config, "booster_expired", "&cExpired!");
            noBoost = new SimpleItem<>(no);
            mainLevel = new PhysicalItem<>(main) {
                @Override public Placeholders getPlaceholders(PlayerStatsInventory inv, int n) { return boosterPlaceholders(inv, n, false); }
            };
            profession = new PhysicalItem<>(prof) {
                @Override public Placeholders getPlaceholders(PlayerStatsInventory inv, int n) { return boosterPlaceholders(inv, n, true); }
            };
        }

        private Placeholders boosterPlaceholders(PlayerStatsInventory inv, int n, boolean includeProfession) {
            List<Booster> boosts = SVFrameMMO.boosters().getActive();
            int index = inv.boostOffset + n;
            if (index >= boosts.size()) return new Placeholders();
            Booster boost = boosts.get(index);
            Placeholders holders = GuiSupport.placeholders(
                    "author", boost.getAuthor() == null || boost.getAuthor().isBlank() ? "Server" : boost.getAuthor(),
                    "value", (int) Math.round(boost.getExtra() * 100d),
                    "left", boost.isTimedOut() ? GuiSupport.colors(expired) : formatDelay(boost.getLeft()));
            if (includeProfession) {
                Profession found = null;
                for (Profession candidate : SVFrameMMO.professions().getAll()) if (candidate.getKey().equalsIgnoreCase(String.valueOf(boost.getTargetKey()))) { found = candidate; break; }
                holders.register("profession", found == null ? String.valueOf(boost.getTargetKey()) : found.getName());
            }
            return holders;
        }

        @Override public boolean hasDifferentDisplay() { return true; }
        @Override public net.minecraft.item.ItemStack getDisplayedItem(PlayerStatsInventory inv, int n) {
            List<Booster> boosts = SVFrameMMO.boosters().getActive();
            int index = inv.boostOffset + n;
            if (index >= boosts.size()) return noBoost.getDisplayedItem(inv, n);
            Booster boost = boosts.get(index);
            boolean professionBoost = boost.getTargetKey() != null && !boost.getTargetKey().isBlank();
            net.minecraft.item.ItemStack stack = (professionBoost ? profession : mainLevel).getDisplayedItem(inv, n);
            stack.setCount(Math.max(1, Math.min(64, index + 1)));
            return stack;
        }
    }

    public final class PlayerStatsInventory extends GeneratedInventory {
        private final PlayerData target;
        private int boostOffset;
        PlayerStatsInventory(PlayerData target, PlayerData opening) { super(new Navigator(opening.getMMOPlayerData()), PlayerStats.this); this.target = target; }
    }

    private static String formatDelay(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long hours = total / 3600L, minutes = total % 3600L / 60L, seconds = total % 60L;
        return hours > 0 ? hours + "h " + minutes + "m" : minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }
}
