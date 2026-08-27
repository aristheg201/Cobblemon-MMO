package vn.svframe.svframemmo.config;

import vn.svframe.svframelib.config.YamlLite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/** Immutable native configuration for the SVFrameMMO progression core. */
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
        boolean saveDefaultClassInfo,
        boolean shareClassExperience,
        boolean shareSkillPoints,
        boolean shareAttributePoints,
        boolean shareSkillReallocationPoints,
        boolean shareAttributeReallocationPoints) {

    public static SVFrameMMOConfig load(Path file) throws IOException {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, Object> defaults = map(root.get("default-playerdata"));
        Map<String, Object> autosave = map(root.get("auto-save"));
        Map<String, Object> combat = map(root.get("combat-log"));
        Map<String, Object> shared = map(first(root, "share-across-classes", "share_across_classes"));
        boolean autosaveEnabled = bool(autosave.get("enabled"), true);
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
                bool(first(root, "save-default-class-info", "save_default_class_info"), false),
                bool(shared.get("experience"), false),
                bool(first(shared, "skill-points", "skill_points"), false),
                bool(first(shared, "attribute-points", "attribute_points"), false),
                bool(first(shared, "skill-reallocation-points", "skill_reallocation_points"), false),
                bool(first(shared, "attribute-reallocation-points", "attribute_reallocation_points"), false));
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(value.toString()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static double number(Object value, double fallback) {
        try { return value instanceof Number number ? number.doubleValue() : value == null ? fallback : Double.parseDouble(value.toString()); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
