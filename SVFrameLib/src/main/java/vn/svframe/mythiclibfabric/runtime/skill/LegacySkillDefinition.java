package vn.svframe.mythiclibfabric.runtime.skill;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Parsed legacy MythicLib skill definition and parameter resolver. */
public record LegacySkillDefinition(String id, String source, String name, String trigger, String passiveType,
                                    Map<String, Parameter> parameters) {
    public record Parameter(String name, ScalingFormula player, double itemDefaultValue) { }

    @SuppressWarnings("unchecked")
    public static LegacySkillDefinition from(String id, Map<String, Object> section) {
        Map<String, Parameter> parsed = new LinkedHashMap<>();
        Object rawParameters = section.get("parameters");
        if (rawParameters instanceof Map<?, ?> rawMap) {
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                Object rawValue = entry.getValue();
                if (rawValue instanceof Number || rawValue instanceof String) {
                    ScalingFormula formula = ScalingFormula.fromConfig(rawValue);
                    parsed.put(key, new Parameter(inferName(key), formula,
                            rawValue instanceof Number number ? number.doubleValue() : 0d));
                    continue;
                }
                if (!(rawValue instanceof Map<?, ?> rawSection)) continue;
                Map<String, Object> parameter = (Map<String, Object>) rawSection;
                Object rawPlayer = parameter.get("player");
                ScalingFormula player = ScalingFormula.fromConfig(rawPlayer);
                parsed.put(key, new Parameter(
                        string(parameter.get("name"), inferName(key)),
                        player,
                        number(parameter.get("item"), 0d)));
            }
        }
        return new LegacySkillDefinition(
                id,
                string(section.get("source"), ""),
                string(section.get("name"), id),
                string(section.get("trigger"), ""),
                string(first(section, "passive-type", "passive_type"), ""),
                Map.copyOf(parsed));
    }

    public Map<String, Object> resolveParameters(Map<String, ?> supplied) {
        return resolveParameters(supplied, null);
    }

    public Map<String, Object> resolveParameters(Map<String, ?> supplied, UUID casterId) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        if (supplied != null) result.putAll(supplied);
        int level = Math.max(1, (int) Math.floor(supplied == null ? 1.0d
                : number(first(supplied, "skill-level", "skill_level", "level"), 1.0d)));
        ServerPlayerEntity player = null;
        if (casterId != null && MythicLibFabricMod.server() != null)
            player = MythicLibFabricMod.server().getPlayerManager().getPlayer(casterId);

        for (Map.Entry<String, Parameter> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Parameter parameter = entry.getValue();
            double playerValue = parameter.player().evaluate(level, player);
            Object direct = supplied == null ? null : supplied.get(key);
            Object modifier = supplied == null ? null : first(supplied, "modifier." + key, "item." + key);
            double itemValue = direct != null ? number(direct, parameter.itemDefaultValue())
                    : modifier != null ? number(modifier, parameter.itemDefaultValue())
                    : parameter.itemDefaultValue();
            double resolved = playerValue + itemValue;
            if (parameter.player().isInteger()) resolved = Math.floor(resolved);
            result.put(key, resolved);
            result.put("parameter." + key, resolved);
            result.put("modifier." + key, itemValue);
        }
        return Map.copyOf(result);
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static String inferName(String key) {
        String normalized = key == null ? "" : key.replace('-', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
