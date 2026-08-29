package vn.svframe.svframemmo.experience.source;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Server-side normalized gameplay signal consumed by MMOCore-style experience sources. */
public record ExperienceSignal(
        String type,
        String primary,
        double units,
        Set<String> tags,
        Map<String, Object> attributes) {

    public ExperienceSignal {
        type = normalize(type);
        primary = primary == null ? "" : primary.trim();
        units = Math.max(0d, units);
        tags = tags == null ? Set.of() : normalizeSet(tags);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static Builder builder(String type) { return new Builder(type); }

    public boolean flag(String key) {
        Object value = attributes.get(key);
        if (value instanceof Boolean flag) return flag;
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    public double number(String key, double fallback) {
        Object value = attributes.get(key);
        try {
            return value instanceof Number number ? number.doubleValue()
                    : value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    public String text(String key) {
        Object value = attributes.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public boolean hasTag(String tag) { return tags.contains(normalize(tag)); }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
    }

    private static Set<String> normalizeSet(Set<String> input) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        input.forEach(value -> result.add(normalize(value)));
        return Set.copyOf(result);
    }

    public static final class Builder {
        private final String type;
        private String primary = "";
        private double units = 1d;
        private final Set<String> tags = new LinkedHashSet<>();
        private final Map<String, Object> attributes = new LinkedHashMap<>();

        private Builder(String type) { this.type = type; }
        public Builder primary(String value) { primary = value; return this; }
        public Builder units(double value) { units = value; return this; }
        public Builder tag(String value) { if (value != null) tags.add(value); return this; }
        public Builder tags(Iterable<String> values) { if (values != null) values.forEach(this::tag); return this; }
        public Builder attribute(String key, Object value) { if (key != null && value != null) attributes.put(key, value); return this; }
        public ExperienceSignal build() { return new ExperienceSignal(type, primary, units, tags, attributes); }
    }
}
