package vn.svframe.svframeitems.config;

import java.util.*;

public final class ConfigValues {
    private ConfigValues() {}

    public static String id(String value) {
        if (value == null) return "";
        String out = value.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        if (!out.matches("[a-z0-9_./:]+")) throw new IllegalArgumentException("Invalid id: " + value);
        return out;
    }

    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object value) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?,?> raw)) throw new IllegalArgumentException("Expected map, got " + value.getClass().getSimpleName());
        Map<String,Object> out = new LinkedHashMap<>();
        raw.forEach((k,v) -> out.put(String.valueOf(k), v));
        return out;
    }
    public static List<Object> list(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> raw) return List.copyOf(raw);
        return List.of(value);
    }
    public static String string(Map<String,Object> map, String key, String fallback) {
        Object value = map.get(key); return value == null ? fallback : String.valueOf(value);
    }
    public static boolean bool(Map<String,Object> map, String key, boolean fallback) {
        Object value = map.get(key); return value == null ? fallback : value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }
    public static int integer(Map<String,Object> map, String key, int fallback) {
        Object value = map.get(key); if (value == null) return fallback;
        if (value instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(value));
    }
    public static long longValue(Map<String,Object> map, String key, long fallback) {
        Object value = map.get(key); if (value == null) return fallback;
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }
    public static double decimal(Map<String,Object> map, String key, double fallback) {
        Object value = map.get(key); if (value == null) return fallback;
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }
    public static <E extends Enum<E>> E enumeration(Map<String,Object> map, String key, Class<E> type, E fallback) {
        String value = string(map, key, null); if (value == null) return fallback;
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
    }
    public static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        for (Object element : list(value)) out.add(String.valueOf(element));
        return List.copyOf(out);
    }
}
