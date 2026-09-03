package vn.svframe.svframemmo.cobblemon.cosmetic;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-layer particle backend metadata for player cosmetics.
 * Geometry/timing remains owned by CosmeticDefinition.Phase; this registry only enriches how each phase emits.
 */
public final class CosmeticEmitterMetadata {
    private static final List<String> ROOT_EMITTER_KEYS = List.of(
            "backend", "color", "scale", "count", "spread", "speed");
    private static final List<String> LAYER_EMITTER_KEYS = List.of(
            "backend", "particle", "color", "scale", "count", "spread", "speed");
    private static final Map<String, Map<CosmeticDefinition.Phase, Emitter>> BY_COSMETIC = new ConcurrentHashMap<>();

    private CosmeticEmitterMetadata() { }

    public static void clear() {
        BY_COSMETIC.clear();
    }

    public static Emitter emitter(CosmeticDefinition definition, CosmeticDefinition.Phase phase) {
        if (definition == null || phase == null) return null;
        Map<CosmeticDefinition.Phase, Emitter> phases = BY_COSMETIC.get(definition.id());
        return phases == null ? null : phases.get(phase);
    }

    public static void register(Path file, CosmeticDefinition definition) {
        if (file == null || definition == null) return;
        try {
            Map<String, Object> root = safeMap(YamlLite.parse(file));
            Map<String, Object> phaseMap = safeMap(root.get("phases"));
            List<Map<String, Object>> rawLayers = flattenSupportedLayers(phaseMap);

            boolean configured = hasAny(root, ROOT_EMITTER_KEYS)
                    || rawLayers.stream().anyMatch(layer -> hasAny(layer, LAYER_EMITTER_KEYS));
            if (!configured) {
                BY_COSMETIC.remove(definition.id());
                return;
            }
            if (rawLayers.size() != definition.phases().size()) {
                SVFrameMMOCobblemon.LOG.warn(
                        "Skipping cosmetic emitter metadata for {} because parsed phase count differs (core={}, emitter={})",
                        file, definition.phases().size(), rawLayers.size());
                BY_COSMETIC.remove(definition.id());
                return;
            }

            Map<String, String> aliases = loadParticleAliases();
            Backend rootBackend = backend(string(root, "backend", "AUTO"), file);
            String rootParticle = aliases.getOrDefault(definition.particleId(), definition.particleId());
            int rootColor = color(root.get("color"), 0xFFFFFF);
            float rootScale = finiteScale(decimal(root.get("scale"), 1.0d), file);
            int rootCount = boundedInt(root.get("count"), 1, 1, 128);
            double rootSpread = boundedDouble(root.get("spread"), 0d, 0d, 8d);
            double rootSpeed = boundedDouble(root.get("speed"), 0d, 0d, 8d);

            IdentityHashMap<CosmeticDefinition.Phase, Emitter> emitters = new IdentityHashMap<>();
            for (int i = 0; i < definition.phases().size(); i++) {
                CosmeticDefinition.Phase phase = definition.phases().get(i);
                Map<String, Object> layer = rawLayers.get(i);
                Backend layerBackend = backend(string(layer, "backend", rootBackend.name()), file);
                String layerParticle = string(layer, "particle", rootParticle);
                layerParticle = aliases.getOrDefault(layerParticle, layerParticle);
                if (!validParticleId(layerParticle))
                    throw new IllegalArgumentException("Invalid cosmetic layer particle '" + layerParticle + "' in " + file);

                emitters.put(phase, new Emitter(
                        layerBackend,
                        layerParticle,
                        color(layer.get("color"), rootColor),
                        finiteScale(decimal(layer.get("scale"), rootScale), file),
                        boundedInt(layer.get("count"), rootCount, 1, 128),
                        boundedDouble(layer.get("spread"), rootSpread, 0d, 8d),
                        boundedDouble(layer.get("speed"), rootSpeed, 0d, 8d)));
            }
            BY_COSMETIC.put(definition.id(), emitters);
        } catch (RuntimeException | java.io.IOException error) {
            throw new IllegalStateException("Could not parse cosmetic emitter metadata from " + file, error);
        }
    }

    private static List<Map<String, Object>> flattenSupportedLayers(Map<String, Object> phaseMap) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : phaseMap.entrySet()) {
            try {
                CosmeticDefinition.Trigger.parse(entry.getKey());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof List<?> list) {
                for (Object layer : list) if (layer instanceof Map<?, ?>) result.add(safeMap(layer));
            } else if (value instanceof Map<?, ?>) {
                result.add(safeMap(value));
            }
        }
        return result;
    }

    private static boolean hasAny(Map<String, Object> map, List<String> keys) {
        for (String key : keys) if (map.containsKey(key)) return true;
        return false;
    }

    private static Backend backend(String raw, Path file) {
        try {
            return Backend.parse(raw);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid cosmetic backend '" + raw + "' in " + file, error);
        }
    }

    private static Map<String, String> loadParticleAliases() throws java.io.IOException {
        Path file = CosmeticDefaults.VFX.resolve("particles.yml");
        if (!Files.isRegularFile(file)) return Map.of();
        Map<String, Object> root = safeMap(YamlLite.parse(file));
        Map<String, Object> aliases = safeMap(root.get("particles"));
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : aliases.entrySet()) {
            Map<String, Object> section = safeMap(entry.getValue());
            String particle = string(section, "particle", "");
            if (!particle.isBlank()) result.put(entry.getKey(), particle);
        }
        return Map.copyOf(result);
    }

    private static Map<String, Object> safeMap(Object value) {
        if (value == null) return Map.of();
        Map<String, Object> map = YamlLite.map(value);
        return map == null ? Map.of() : map;
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        try {
            return value instanceof Number number ? number.intValue()
                    : value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) { return fallback; }
    }

    private static double decimal(Object value, double fallback) {
        try {
            return value instanceof Number number ? number.doubleValue()
                    : value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) { return fallback; }
    }

    private static int color(Object value, int fallback) {
        try {
            if (value instanceof Number number) {
                int rgb = number.intValue();
                return rgb >= 0 && rgb <= 0xFFFFFF ? rgb : fallback;
            }
            if (value == null) return fallback;
            String raw = String.valueOf(value).trim();
            if (raw.startsWith("#")) raw = raw.substring(1);
            else if (raw.startsWith("0x") || raw.startsWith("0X")) raw = raw.substring(2);
            return raw.matches("[0-9a-fA-F]{6}") ? Integer.parseInt(raw, 16) : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }

    private static int boundedInt(Object value, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, integer(value, fallback)));
    }

    private static double boundedDouble(Object value, double fallback, double min, double max) {
        double parsed = decimal(value, fallback);
        if (!Double.isFinite(parsed)) parsed = fallback;
        return Math.max(min, Math.min(max, parsed));
    }

    private static float finiteScale(double value, Path file) {
        if (!Double.isFinite(value) || value < 0.01d || value > 4d)
            throw new IllegalArgumentException("Invalid cosmetic particle scale in " + file);
        return (float) value;
    }

    private static boolean validParticleId(String value) {
        return value != null && value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") && !value.contains("..");
    }

    public enum Backend {
        AUTO, MINECRAFT, COBBLEMON;

        static Backend parse(String value) {
            if (value == null || value.isBlank()) return AUTO;
            return valueOf(value.trim().replace('-', '_').replace(' ', '_').toUpperCase(java.util.Locale.ROOT));
        }
    }

    public record Emitter(Backend backend, String particleId, int colorRgb, float scale,
                          int count, double spread, double speed) { }
}
