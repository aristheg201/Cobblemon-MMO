package vn.svframe.compat;

import com.google.gson.*;
import java.util.*;

/** Keeps block IDs exact; legacy singular ore names are aliases ONLY in the tags filter. */
public final class GuildMiningFilters {
    private static final Map<String, String> TAG_ALIASES = Map.of(
        "minecraft:coal_ore", "minecraft:coal_ores", "minecraft:copper_ore", "minecraft:copper_ores",
        "minecraft:iron_ore", "minecraft:iron_ores", "minecraft:gold_ore", "minecraft:gold_ores",
        "minecraft:lapis_ore", "minecraft:lapis_ores", "minecraft:redstone_ore", "minecraft:redstone_ores",
        "minecraft:emerald_ore", "minecraft:emerald_ores", "minecraft:diamond_ore", "minecraft:diamond_ores");
    private GuildMiningFilters() {}

    private static String tag(String value) {
        String id = value.startsWith("#") ? value.substring(1) : value;
        return TAG_ALIASES.getOrDefault(id, id);
    }
    public static boolean matches(JsonObject filters, Map<String, Object> attributes) {
        for (var row : filters.entrySet()) {
            Object actual = attributes.get(row.getKey());
            if (actual == null) return false;
            boolean tags = row.getKey().equals("tags");
            List<String> values = actual instanceof Collection<?> collection
                ? collection.stream().map(String::valueOf).map(s -> tags ? tag(s) : s).toList()
                : List.of(tags ? tag(String.valueOf(actual)) : String.valueOf(actual));
            JsonElement expected = row.getValue();
            if (expected.isJsonArray()) {
                boolean found = false;
                for (JsonElement entry : expected.getAsJsonArray())
                    if (values.contains(tags ? tag(entry.getAsString()) : entry.getAsString())) found = true;
                if (!found) return false;
            } else if (expected.isJsonObject()) {
                if (!(actual instanceof Number number)) return false;
                JsonObject range = expected.getAsJsonObject();
                double value = number.doubleValue();
                if (range.has("min") && value < range.get("min").getAsDouble()
                    || range.has("max") && value > range.get("max").getAsDouble()) return false;
            } else if (!values.contains(tags ? tag(expected.getAsString()) : expected.getAsString())) return false;
        }
        return true;
    }
}
