package vn.svframe.svframecore;

import vn.svframe.svframelib.config.YamlLite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Native config slice for subsystems already ported. */
public record SVFrameCoreConfig(long playerResourceTickPeriod, long combatLogTicks) {
    public static SVFrameCoreConfig load(Path file) throws IOException {
        if (!Files.exists(file)) return new SVFrameCoreConfig(20L, 200L);
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        long resourcePeriod = Math.max(1L, number(root.get("player_resource_tick_period"), 20L));
        Map<String, Object> combat = map(root.get("combat-log"));
        long combatSeconds = Math.max(1L, number(combat.get("timer"), 10L));
        return new SVFrameCoreConfig(resourcePeriod, combatSeconds * 20L);
    }

    private static long number(Object value, long fallback) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return fallback;
        try { return Long.parseLong(String.valueOf(value)); } catch (NumberFormatException ignored) { return fallback; }
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return value instanceof Map<?,?> raw ? (Map<String,Object>) raw : Map.of(); }
}
