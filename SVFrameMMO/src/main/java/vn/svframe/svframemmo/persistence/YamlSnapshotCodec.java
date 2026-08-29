package vn.svframe.svframemmo.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import vn.svframe.svframelib.config.YamlLite;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts snapshot DTOs to/from readable YAML without platform configuration APIs. */
final class YamlSnapshotCodec {
    private static final Type MAP_TYPE = new TypeToken<LinkedHashMap<String, Object>>() { }.getType();
    private final Gson gson = new GsonBuilder().create();

    PlayerDataSnapshot decode(String yaml) {
        Map<String, Object> map = YamlLite.map(YamlLite.parse(yaml));
        return gson.fromJson(gson.toJson(map), PlayerDataSnapshot.class);
    }

    String encode(PlayerDataSnapshot snapshot) {
        Map<String, Object> map = gson.fromJson(gson.toJson(snapshot), MAP_TYPE);
        StringBuilder out = new StringBuilder(4096);
        writeMap(out, map, 0);
        return out.toString();
    }

    private static void writeMap(StringBuilder out, Map<?, ?> map, int indent) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            indent(out, indent).append(safeKey(String.valueOf(entry.getKey()))).append(':');
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                if (nested.isEmpty()) out.append(" {}\n");
                else { out.append('\n'); writeMap(out, nested, indent + 2); }
            } else if (value instanceof Iterable<?> iterable) {
                List<Object> values = new ArrayList<>();
                iterable.forEach(values::add);
                if (values.isEmpty()) out.append(" []\n");
                else {
                    out.append('\n');
                    for (Object item : values) indent(out, indent + 2).append("- ").append(scalar(item)).append('\n');
                }
            } else out.append(' ').append(scalar(value)).append('\n');
        }
    }

    private static StringBuilder indent(StringBuilder out, int count) { return out.append(" ".repeat(Math.max(0, count))); }
    private static String safeKey(String key) { return key.matches("[A-Za-z0-9_.-]+") ? key : quote(key); }
    private static String scalar(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (Double.isFinite(decimal) && decimal == Math.rint(decimal)) return Long.toString(number.longValue());
            return String.valueOf(value);
        }
        String text = String.valueOf(value);
        return text.matches("[A-Za-z0-9_./:+-]+") && !text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")
                && !text.equalsIgnoreCase("null") ? text : quote(text);
    }
    private static String quote(String value) { return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"'; }
}
