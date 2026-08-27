package vn.svframe.svframemmo.config;

import vn.svframe.svframelib.config.YamlLite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public record SVFrameMMOConfig(int resourceTickPeriod, int combatTimerSeconds, int autosaveSeconds,
                               int defaultLevel, int defaultClassPoints, int defaultSkillPoints,
                               int defaultAttributePoints, double defaultMana, double defaultStamina,
                               double defaultStellium, boolean passiveSkillNeedsBinding) {
    public static SVFrameMMOConfig load(Path file) throws IOException {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        Map<String, Object> defaults = map(root.get("default-playerdata"));
        Map<String, Object> autosave = map(root.get("auto-save"));
        Map<String, Object> combat = map(root.get("combat-log"));
        boolean autosaveEnabled = bool(autosave.get("enabled"), true);
        return new SVFrameMMOConfig(
                integer(root.get("player_resource_tick_period"), 20),
                integer(combat.get("timer"), 10),
                autosaveEnabled ? integer(autosave.get("interval"), 1800) : 0,
                integer(defaults.get("level"), 1),
                integer(defaults.get("class-points"), 0),
                integer(defaults.get("skill-points"), 0),
                integer(defaults.get("attribute-points"), 0),
                number(defaults.get("mana"), 1000),
                number(defaults.get("stamina"), 1000),
                number(defaults.get("stellium"), 1000),
                bool(root.get("passive-skill-need-bound"), true));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(); }
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
