package vn.svframe.svframemmo.runtime;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.config.YamlLite;
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

/** Permanent numeric HUD. Vanilla-heart limiting is presentation-only in the network mixin. */
public final class PersistentHudRuntime implements ModInitializer {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-HUD");
    private static final int GLOBAL_SKILL_SLOTS = 6;
    private static volatile double visualHealthCap = 40d;
    private static volatile boolean initialized;

    private volatile SVFrameMMOConfig observedConfig;
    private volatile HudOptions options = HudOptions.defaults();

    public static double visualHealthCap() { return visualHealthCap; }

    /** True only when this runtime is guaranteed to submit the higher-priority idle HUD this same tick. */
    public static boolean willOverrideIdle(PlayerData data, MMOPlayerData mmo, SVFrameMMOConfig live, long tick) {
        if (!initialized || data == null || mmo == null || live == null || !live.actionBar().enabled()) return false;
        int period = Math.max(1, live.actionBar().updateTicks());
        if (tick % period != 0L || !data.isOnline()) return false;
        ServerPlayerEntity player = data.getPlayer();
        return player != null && !player.isDead() && mmo.isOnline() && mmo.getPlayer() == player;
    }

    @Override
    public void onInitialize() {
        initialized = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> tick(server, SVFrameMMO.currentTick()));
    }

    private void tick(MinecraftServer server, long tick) {
        SVFrameMMOConfig live;
        try { live = SVFrameMMO.config(); }
        catch (IllegalStateException ignored) { return; }
        if (live != observedConfig) reloadOptions(live);

        int period = Math.max(1, live.actionBar().updateTicks());
        if (tick % period != 0L) return;

        HudOptions hud = options;
        for (PlayerData data : SVFrameMMO.playerData().online()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(data.getUniqueId());
            if (player == null || !data.isOnline() || data.getPlayer() != player) continue;

            MMOPlayerData mmo = MMOPlayerData.getOrNull(data.getUniqueId());
            if (mmo == null || !mmo.isOnline() || mmo.getPlayer() != player) continue;

            if (!live.actionBar().enabled() || player.isDead()) continue;
            boolean casting = SVFrameMMO.skillBar().isCasting(data.getUniqueId());
            if (casting && !hud.alwaysVisible()) continue;

            String resourceFormat = casting ? hud.castingFormat() : live.actionBar().format();
            String base = formatBase(data, mmo, resourceFormat);
            String output = casting
                    ? base + hud.castingSeparator() + formatSkills(data, mmo, live.skillCasting(), hud.skillNameMaxLength())
                    : base;
            mmo.getActionBar().show(hud.priority(), Math.max(2L, period + 2L), SVFrameLib.inst().parseColors(output));
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
                            "&c❤&f{health}/{max_health} &b✦&f{mana} &e⚡&f{stamina} &d✹&f{stellium} &7⛨&f{armor}"),
                    Math.max(6, Math.min(32, integer(first(section, "skill-name-max-length", "skill_name_max_length"), 14)))
            );
        } catch (Exception exception) {
            LOG.log(Level.WARNING, "Could not read extended action-bar HUD options; using safe defaults", exception);
        }
        options = next;
        visualHealthCap = next.maxVanillaHealth();
    }

    private static String formatBase(PlayerData data, MMOPlayerData mmo, String raw) {
        String format = raw == null || raw.isBlank()
                ? "&c❤ &f{health}/{max_health} &8| &b✦ &f{mana}/{max_mana} &8| &e⚡ &f{stamina}/{max_stamina} &8| &d✹ &f{stellium}/{max_stellium} &8| &7⛨ &f{armor}"
                : raw;
        return format
                .replace("{health}", trim(data.getHealth()))
                .replace("{max_health}", trim(data.getMaxResource(PlayerResource.HEALTH)))
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
                    level = Math.max(1, SVFrameMMO.externalProgression().level(data.getUniqueId(), skill.getSkill().getId()));
                    break;
                }
            } else {
                PlayerSkillCatalog.Entry entry = bindings.get(slot);
                if (entry != null) { skill = entry.skill(); level = Math.max(1, entry.level()); }
            }
            if (skill == null) { parts.add("&8" + slot + "›—"); continue; }

            double cooldown = mmo.getCooldownMap().getCooldown(skill.getCooldownPath());
            double mana = parameter(skill, "mana", level, data);
            double stamina = parameter(skill, "stamina", level, data);
            String template = cooldown > 0.01d ? style.onCooldown()
                    : data.getMana() + 1.0e-6 < mana ? style.noMana()
                    : data.getStamina() + 1.0e-6 < stamina ? style.noStamina() : style.ready();
            parts.add(template.replace("{index}", Integer.toString(slot))
                    .replace("{skill}", compactSkillName(skill.getSkill().getName(), maxNameLength))
                    .replace("{cooldown}", cooldown(cooldown)).replace("{level}", Integer.toString(level)));
        }
        return String.join(style.split(), parts);
    }

    private static String compactSkillName(String raw, int maxLength) {
        String name = raw == null ? "Skill" : raw.trim();
        if (name.length() <= maxLength) return name;
        return maxLength <= 1 ? "…" : name.substring(0, maxLength - 1).stripTrailing() + "…";
    }

    private static double parameter(ClassSkill skill, String id, int level, PlayerData data) {
        return skill.getParameters().containsKey(id) ? Math.max(0d, skill.getParameter(id, level, data)) : 0d;
    }
    private static double maxResource(MMOPlayerData mmo, PlayerResource resource) { return Math.max(0d, mmo.getStatMap().getStat(resource.getMaxStat())); }
    private static String trim(double value) {
        if (!Double.isFinite(value)) return "0";
        double rounded = Math.round(value * 10d) / 10d;
        return Math.rint(rounded) == rounded ? Long.toString((long) rounded) : String.format(Locale.ROOT, "%.1f", rounded);
    }
    private static String cooldown(double value) {
        if (value <= 0.01d) return "0";
        if (value < 10d) return String.format(Locale.ROOT, "%.1f", value);
        return Long.toString((long) Math.ceil(value));
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        raw.forEach((key, item) -> out.put(String.valueOf(key), item));
        return out;
    }
    private static Object first(Map<String, Object> map, String... keys) { for (String key : keys) if (map.containsKey(key)) return map.get(key); return null; }
    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static double number(Object value, double fallback) {
        try { return value instanceof Number number ? number.doubleValue() : value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(Object value, boolean fallback) { return value instanceof Boolean flag ? flag : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }

    private record HudOptions(boolean alwaysVisible, int priority, double maxVanillaHealth, String castingSeparator,
                              String castingFormat, int skillNameMaxLength) {
        private static HudOptions defaults() {
            return new HudOptions(true, 100, 40d, " &8│&r ",
                    "&c❤&f{health}/{max_health} &b✦&f{mana} &e⚡&f{stamina} &d✹&f{stellium} &7⛨&f{armor}", 14);
        }
    }
}
