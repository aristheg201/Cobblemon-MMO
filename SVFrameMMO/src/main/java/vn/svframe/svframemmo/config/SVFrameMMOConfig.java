package vn.svframe.svframemmo.config;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.config.YamlLite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Immutable native configuration for SVFrameMMO runtime and progression. */
public record SVFrameMMOConfig(
        int resourceTickPeriod,
        int combatTimerSeconds,
        int autosaveSeconds,
        int defaultLevel,
        int defaultClassPoints,
        int defaultSkillPoints,
        int defaultAttributePoints,
        int defaultReallocationPoints,
        double defaultHealth,
        double defaultMana,
        double defaultStamina,
        double defaultStellium,
        boolean passiveSkillNeedsBinding,
        boolean canCreativeCast,
        boolean saveDefaultClassInfo,
        boolean shareClassExperience,
        boolean shareSkillPoints,
        boolean shareAttributePoints,
        boolean shareSkillReallocationPoints,
        boolean shareAttributeReallocationPoints,
        SkillCasting skillCasting,
        ActionBar actionBar) {

    /** MMOCore-compatible SKILL_BAR configuration. Time-out is expressed in server ticks. */
    public record SkillCasting(String mode, String openKey, boolean ignoreSneak, boolean useLowestKeybinds, int timeoutTicks,
                               PlayerMessage enterMessage, PlayerMessage quitMessage, SkillBarActionBar actionBar) {
        public boolean skillBarMode() { return "SKILL_BAR".equals(mode); }
        public boolean opensWithSwapHands() { return "SWAP_HANDS".equals(openKey); }
    }

    /** MMOCore PlayerMessage-compatible subset used by skill-casting enter/quit feedback. */
    public record PlayerMessage(String message, boolean actionBar, int duration, int priority, String sound) { }
    public record SkillBarActionBar(String split, String ready, String onCooldown, String noMana, String noStamina) { }
    public record ActionBar(boolean enabled, int updateTicks, String format) { }

    public static SVFrameMMOConfig load(Path file) throws IOException {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, Object> defaults = map(root.get("default-playerdata"));
        Map<String, Object> autosave = map(root.get("auto-save"));
        Map<String, Object> combat = map(root.get("combat-log"));
        Map<String, Object> shared = map(first(root, "share-across-classes", "share_across_classes"));
        Map<String, Object> casting = map(first(root, "skill-casting", "skill_casting"));
        Map<String, Object> skillBar = map(first(casting, "action-bar", "action_bar"));
        Map<String, Object> messages = map(casting.get("message"));
        Map<String, Object> enter = map(messages.get("enter"));
        Map<String, Object> quit = map(messages.get("quit"));
        Map<String, Object> actionBar = map(first(root, "action-bar", "action_bar"));
        boolean autosaveEnabled = bool(autosave.get("enabled"), true);

        String castingMode = UtilityMethods.enumName(string(casting.get("mode"), "SKILL_BAR"));
        if (!"SKILL_BAR".equals(castingMode))
            throw new IOException("SVFrameMMO currently supports MMOCore SKILL_BAR casting mode only, got: " + castingMode);
        int timeout = casting.containsKey("time-out") ? integer(casting.get("time-out"), -1) : 0;
        if (casting.containsKey("time-out") && timeout <= 0)
            throw new IOException("skill-casting.time-out must be strictly positive when configured");

        SkillCasting skillCasting = new SkillCasting(
                castingMode,
                UtilityMethods.enumName(string(casting.get("open"), "SWAP_HANDS")),
                bool(first(casting, "ignore-sneak", "disable-sneak"), false),
                bool(first(casting, "use-lowest-keybinds", "use_lowest_keybinds"), true),
                Math.max(0, timeout),
                message(enter, "&e&l☄ &a&lSKILL CASTING &e&l☄", true, 20, 31, "BLOCK_END_PORTAL_FRAME_FILL,1,2"),
                message(quit, "&e&l☄ &c&lSKILL CASTING &e&l☄", true, 20, 31, "BLOCK_FIRE_EXTINGUISH,1,2"),
                new SkillBarActionBar(
                        string(skillBar.get("split"), "&7 &7 - &7 "),
                        string(skillBar.get("ready"), "&6[{index}] &a&l{skill}"),
                        string(skillBar.get("on-cooldown"), "&6[{index}] &c&l{skill} &6(&c{cooldown}&6)"),
                        string(skillBar.get("no-mana"), "&6[{index}] &9&l{skill}"),
                        string(skillBar.get("no-stamina"), "&6[{index}] &9&l{skill}")));

        ActionBar defaultActionBar = new ActionBar(
                bool(actionBar.get("enabled"), true),
                Math.max(1, integer(first(actionBar, "ticks-to-update", "ticks_to_update"), 5)),
                string(actionBar.get("format"), "&c❤ {health}/{max_health} &f| {mana_icon} {mana}/{max_mana} &f| &7⛨ {armor}"));

        return new SVFrameMMOConfig(
                Math.max(1, integer(first(root, "player-resource-tick-period", "player_resource_tick_period"), 20)),
                Math.max(0, integer(combat.get("timer"), 10)),
                autosaveEnabled ? Math.max(1, integer(autosave.get("interval"), 1800)) : 0,
                Math.max(1, integer(defaults.get("level"), 1)),
                Math.max(0, integer(defaults.get("class-points"), 0)),
                Math.max(0, integer(defaults.get("skill-points"), 0)),
                Math.max(0, integer(defaults.get("attribute-points"), 0)),
                Math.max(0, integer(defaults.get("reallocation-points"), 0)),
                Math.max(1d, number(defaults.get("health"), 20d)),
                Math.max(0d, number(defaults.get("mana"), 100d)),
                Math.max(0d, number(defaults.get("stamina"), 100d)),
                Math.max(0d, number(defaults.get("stellium"), 100d)),
                bool(first(root, "passive-skill-needs-binding", "passive-skill-need-bound"), true),
                bool(root.get("can-creative-cast"), false),
                bool(first(root, "save-default-class-info", "save_default_class_info"), false),
                bool(shared.get("experience"), false),
                bool(first(shared, "skill-points", "skill_points"), false),
                bool(first(shared, "attribute-points", "attribute_points"), false),
                bool(first(shared, "skill-reallocation-points", "skill_reallocation_points"), false),
                bool(first(shared, "attribute-reallocation-points", "attribute_reallocation_points"), false),
                skillCasting, defaultActionBar);
    }

    private static PlayerMessage message(Map<String, Object> section, String fallback, boolean actionBar,
                                         int duration, int priority, String sound) {
        return new PlayerMessage(
                string(section.get("message"), fallback),
                bool(first(section, "action-bar", "action_bar"), actionBar),
                Math.max(1, integer(section.get("duration"), duration)),
                integer(section.get("priority"), priority),
                string(section.get("sound"), sound));
    }

    private static Object first(Map<String, Object> map, String... keys) { for (String key : keys) if (map.containsKey(key)) return map.get(key); return null; }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static int integer(Object value, int fallback) { try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(value.toString()); } catch (RuntimeException ignored) { return fallback; } }
    private static double number(Object value, double fallback) { try { return value instanceof Number number ? number.doubleValue() : value == null ? fallback : Double.parseDouble(value.toString()); } catch (RuntimeException ignored) { return fallback; } }
    private static boolean bool(Object value, boolean fallback) { if (value instanceof Boolean flag) return flag; return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
}
