package vn.svframe.svframecore.player;

import vn.svframe.svframecore.api.player.PlayerData;

import java.util.Map;

/** Exact default-playerdata value set from SVFrameCore 1.13.1. */
public record DefaultPlayerData(
        int level,
        int classPoints,
        int skillPoints,
        int attributePoints,
        int attributeReallocationPoints,
        int skillReallocationPoints,
        int skillTreeReallocationPoints,
        double health,
        double mana,
        double stamina,
        double stellium) {

    public static final DefaultPlayerData DEFAULT = new DefaultPlayerData(1, 0, 0, 0, 0, 0, 0, 20d, 0d, 0d, 0d);

    public static DefaultPlayerData from(Map<String, Object> config) {
        return new DefaultPlayerData(
                integer(config.get("level"), 1),
                integer(config.get("class-points"), 0),
                integer(config.get("skill-points"), 0),
                integer(config.get("attribute-points"), 0),
                integer(config.get("attribute-realloc-points"), 0),
                integer(config.get("skill-realloc-points"), 0),
                integer(config.get("skill-tree-realloc-points"), 0),
                decimal(config.get("health"), 20d),
                decimal(config.get("mana"), 20d),
                decimal(config.get("stamina"), 20d),
                decimal(config.get("stellium"), 20d));
    }

    public void apply(PlayerData player) {
        player.setLevel(level);
        player.setExperience(0d);
        player.setClassPoints(classPoints);
        player.setSkillPoints(skillPoints);
        player.setAttributePoints(attributePoints);
        player.setAttributeReallocationPoints(attributeReallocationPoints);
        player.setSkillReallocationPoints(skillReallocationPoints);
        player.setSkillTreeReallocationPoints(skillTreeReallocationPoints);
        player.loadResources(health, mana, stamina, stellium);
    }

    private static int integer(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static double decimal(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return fallback;
        try { return Double.parseDouble(String.valueOf(value)); } catch (NumberFormatException ignored) { return fallback; }
    }
}
