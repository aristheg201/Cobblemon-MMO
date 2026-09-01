package vn.svframe.svframemmo.runtime;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.StatInstance;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.config.DefaultFiles;
import vn.svframe.svframemmo.config.SVFrameMMOConfig;
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the permanent player HUD. Resource information never disappears when the
 * player enters skill-casting mode; the six cast slots are appended to the same
 * action bar instead of competing for another ActionBarHandler priority.
 */
public final class PersistentHudRuntime implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-HUD");
    private static final String HEALTH_CAP_KEY = "svframemmo_hud_health_cap";
    private static final int GLOBAL_SKILL_SLOTS = 6;

    private volatile SVFrameMMOConfig observedConfig;
    private volatile HudOptions options = HudOptions.defaults();

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server, SVFrameMMO.currentTick()));
    }

    private void tick(MinecraftServer server, long tick) {
        SVFrameMMOConfig live;
        try {
            live = SVFrameMMO.config();
        } catch (IllegalStateException ignored) {
            return;
        }
        if (live != observedConfig) reloadOptions(live);

        int period = Math.max(1, live.actionBar().updateTicks());
        if (tick % period != 0L) return;

        HudOptions hud = options;
        for (PlayerData data : SVFrameMMO.playerData().all()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(data.getUniqueId());
            if (player == null || !data.isOnline() || data.getPlayer() != player) continue;

            // A disconnect can invalidate SVFrameLib before SVFrameMMO receives its
            // own disconnect callback. Never call PlayerData#getMMOPlayerData here:
            // that method may setup() the cached player again and revive a session
            // which SVFrameLib has already marked offline.
            MMOPlayerData mmo = MMOPlayerData.getOrNull(data.getUniqueId());
            if (mmo == null || !mmo.isOnline() || mmo.getPlayer() != player) continue;

            // HUD ownership is based on the native SVFrameMMO PlayerData lifecycle, not
            // SVFrameLib profile readiness. Classless servers legitimately use the
            // fallback MMO session before/without choosing a profile.
            enforceHealthCap(data, mmo, hud.maxVanillaHealth());

            if (!live.actionBar().enabled() || player.isDead()) continue;
            boolean casting = SVFrameMMO.skillBar().isCasting(data.getUniqueId());
            if (casting && !hud.alwaysVisible()) continue;

            // A single Minecraft action bar has finite horizontal space. Keep the full
            // resource readout while idle, then switch only the resource portion to a
            // compact layout while the six skill slots are visible. Resources therefore
            // remain permanently visible without turning the action bar into a debug log.
            String resourceFormat = casting ? hud.castingFormat() : live.actionBar().format();
            String base = formatBase(data, mmo, resourceFormat, hud.maxVanillaHealth());
            String output = casting
                    ? base + hud.castingSeparator() + formatSkills(data, mmo, live.skillCasting(), hud.skillNameMaxLength())
                    : base;
            String rendered = SVFrameLib.inst().parseColors(output);
            mmo.getActionBar().show(hud.priority(), Math.max(2L, period + 2L), rendered);
        }
    }

    private void reloadOptions(SVFrameMMOConfig live) {
        observedConfig = live;
        HudOptions next = HudOptions.defaults();
        try {
            Map<String, Object> root = YamlLite.map(YamlLite.parse(DefaultFiles.ROOT.resolve("config.yml")));
            Map<String, Object> section = map(first(root, "action-bar", "action_bar"));
            next = new HudOptions(
                    bool(first(section, "always-visible", "always_visible"), true),
                    Math.max(50, integer(section.get("priority"), 100)),
                    clamp(number(first(section, "max-vanilla-health", "max_vanilla_health"), 40d), 2d, 1024d),
                    string(first(section, "casting-separator", "casting_separator"), " &8│&r "),
                    string(first(section, "casting-format", "casting_format"),
                            "&c❤&f{health} &b✦&f{mana} &e⚡&f{stamina} &d✹&f{stellium} &7⛨&f{armor}"),
                    Math.max(6, Math.min(32, integer(first(section, "skill-name-max-length", "skill_name_max_length"), 14)))
            );
        } catch (Exception exception) {
            LOG.log(Level.WARNING, "Could not read extended action-bar HUD options; using safe defaults", exception);
        }
        options = next;
    }

    private static String formatBase(PlayerData data, MMOPlayerData mmo, String raw, double healthCap) {
        String format = raw == null || raw.isBlank()
                ? "&c❤ &f{health}/{max_health} &8| &b✦ &f{mana}/{max_mana} &8| &e⚡ &f{stamina}/{max_stamina} &8| &d✹ &f{stellium}/{max_stellium} &8| &7⛨ &f{armor}"
                : raw;
        double maxHealth = healthCap > 0d
                ? Math.min(data.getMaxResource(PlayerResource.HEALTH), healthCap)
                : data.getMaxResource(PlayerResource.HEALTH);
        return format
                .replace("{health}", trim(data.getHealth()))
                .replace("{max_health}", trim(maxHealth))
                .replace("{mana_icon}", "&b✦")
                .replace("{mana}", trim(data.getMana()))
                .replace("{max_mana}", trim(maxResource(mmo, PlayerResource.MANA)))
                .replace("{stamina_icon}", "&e⚡")
                .replace("{stamina}", trim(data.getStamina()))
                .replace("{max_stamina}", trim(maxResource(mmo, PlayerResource.STAMINA)))
                .replace("{stellium_icon}", "&d✹")
                .replace("{stellium}", trim(data.getStellium()))
                .replace("{max_stellium}", trim(maxResource(mmo, PlayerResource.STELLIUM)))
                .replace("{armor}", trim(mmo.getStatMap().getStat("ARMOR")))
                .replace("{level}", Integer.toString(data.getLevel()));
    }

    private static String formatSkills(PlayerData data, MMOPlayerData mmo, SVFrameMMOConfig.SkillCasting casting, int maxNameLength) {
        var temporary = SVFrameMMO.temporarySkills().slots(data.getUniqueId());
        Map<Integer, PlayerSkillCatalog.Entry> bindings = temporary.isEmpty() ? PlayerSkillCatalog.bindings(data) : Map.of();
        SVFrameMMOConfig.SkillBarActionBar style = casting.actionBar();
        List<String> parts = new ArrayList<>(GLOBAL_SKILL_SLOTS);
        for (int slot = 1; slot <= GLOBAL_SKILL_SLOTS; slot++) {
            ClassSkill skill = null;
            int level = 1;

            if (!temporary.isEmpty()) {
                for (var overlaySlot : temporary) {
                    if (overlaySlot.slot() != slot) continue;
                    skill = overlaySlot.skill();
                    int externalLevel = SVFrameMMO.externalProgression().level(data.getUniqueId(), skill.getSkill().getId());
                    level = Math.max(1, externalLevel);
                    break;
                }
            } else {
                PlayerSkillCatalog.Entry entry = bindings.get(slot);
                if (entry != null) {
                    skill = entry.skill();
                    level = Math.max(1, entry.level());
                }
            }

            if (skill == null) {
                parts.add("&8" + slot + "›—");
                continue;
            }

            double cooldown = mmo.getCooldownMap().getCooldown(skill.getCooldownPath());
            double mana = parameter(skill, "mana", level, data);
            double stamina = parameter(skill, "stamina", level, data);

            String template;
            if (cooldown > 0.01d) template = style.onCooldown();
            else if (data.getMana() + 1.0e-6 < mana) template = style.noMana();
            else if (data.getStamina() + 1.0e-6 < stamina) template = style.noStamina();
            else template = style.ready();

            parts.add(template
                    .replace("{index}", Integer.toString(slot))
                    .replace("{skill}", compactSkillName(skill.getSkill().getName(), maxNameLength))
                    .replace("{cooldown}", cooldown(cooldown))
                    .replace("{level}", Integer.toString(level)));
        }
        return String.join(style.split(), parts);
    }

    private static String compactSkillName(String raw, int maxLength) {
        String name = raw == null ? "Skill" : raw.trim();
        if (name.length() <= maxLength) return name;
        if (maxLength <= 1) return "…";
        return name.substring(0, maxLength - 1).stripTrailing() + "…";
    }

    private static double parameter(ClassSkill skill, String id, int level, PlayerData data) {
        return skill.getParameters().containsKey(id) ? Math.max(0d, skill.getParameter(id, level, data)) : 0d;
    }

    private static double maxResource(MMOPlayerData mmo, PlayerResource resource) {
        return Math.max(0d, mmo.getStatMap().getStat(resource.getMaxStat()));
    }

    /**
     * Applies a final flat modifier calculated after every other modifier. This
     * caps the actual SVFrameLib MAX_HEALTH value, so vanilla never renders a
     * third heart row even when equipment/fusion adds more health.
     */
    private static void enforceHealthCap(PlayerData data, MMOPlayerData mmo, double cap) {
        if (cap <= 0d) return;
        StatInstance health = mmo.getStatMap().getInstance("MAX_HEALTH");

        double flat = health.getBase();
        double additive = 1d;
        double relative = 1d;
        StatModifier oldCap = null;
        for (StatModifier modifier : health.getModifiers()) {
            if (HEALTH_CAP_KEY.equals(modifier.getKey())) {
                oldCap = modifier;
                continue;
            }
            if (modifier.getType() == ModifierType.FLAT) flat += modifier.getValue();
            else if (modifier.getType() == ModifierType.ADDITIVE_MULTIPLIER) additive += modifier.getValue() / 100d;
            else if (modifier.getType() == ModifierType.RELATIVE) relative *= 1d + modifier.getValue() / 100d;
        }

        double multiplier = additive * relative;
        double uncapped = flat * multiplier;
        boolean needsCap = Double.isFinite(uncapped) && uncapped > cap + 1.0e-4;
        if (!needsCap) {
            if (oldCap != null) {
                health.remove(HEALTH_CAP_KEY);
                health.update();
            }
        } else {
            double correction = Math.abs(multiplier) > 1.0e-9
                    ? cap / multiplier - flat
                    : cap - uncapped;
            if (oldCap == null || Math.abs(oldCap.getValue() - correction) > 1.0e-4) {
                if (oldCap != null) health.remove(HEALTH_CAP_KEY);
                health.registerModifier(new StatModifier(HEALTH_CAP_KEY, "MAX_HEALTH", correction));
                health.update();
            }
        }

        if (data.getHealth() > cap)
            data.setResource(PlayerResource.HEALTH, cap, ResourceUpdateReason.CLAMPING);
    }

    private static String trim(double value) {
        if (!Double.isFinite(value)) return "0";
        double rounded = Math.round(value * 10d) / 10d;
        return Math.rint(rounded) == rounded
                ? Long.toString((long) rounded)
                : String.format(Locale.ROOT, "%.1f", rounded);
    }

    private static String cooldown(double value) {
        if (value <= 0.01d) return "0";
        if (value < 10d) return String.format(Locale.ROOT, "%.1f", value);
        return Long.toString((long) Math.ceil(value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        raw.forEach((key, item) -> out.put(String.valueOf(key), item));
        return out;
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static double number(Object value, double fallback) {
        try { return value instanceof Number number ? number.doubleValue() : value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private record HudOptions(
            boolean alwaysVisible,
            int priority,
            double maxVanillaHealth,
            String castingSeparator,
            String castingFormat,
            int skillNameMaxLength
    ) {
        private static HudOptions defaults() {
            return new HudOptions(
                    true,
                    100,
                    40d,
                    " &8│&r ",
                    "&c❤&f{health} &b✦&f{mana} &e⚡&f{stamina} &d✹&f{stellium} &7⛨&f{armor}",
                    14
            );
        }
    }
}
