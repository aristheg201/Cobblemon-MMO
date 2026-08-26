package vn.svframe.svframelib.skill.parameter;

import vn.svframe.svframelib.skill.parameter.value.NonScalingFormula;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframelib.util.configobject.MapConfigObject;

import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** MythicLib 1.7.1 skill-parameter descriptor adapted to native config objects. */
public class SkillParameter {
    public static final DecimalFormat DEFAULT_DECIMAL_FORMAT = new DecimalFormat("0.####");

    private final String translate;
    private final double itemDefault;
    private final ScalingFormula formula;
    private final DecimalFormat format;

    public SkillParameter(String translate, double itemDefault, ScalingFormula formula, DecimalFormat format) {
        this.translate = translate;
        this.itemDefault = itemDefault;
        this.formula = formula == null ? ScalingFormula.ZERO : formula;
        this.format = format;
    }

    public String getTranslate() { return translate; }
    public double getItemDefaultValue() { return itemDefault; }
    public DecimalFormat getDecimalFormat() { return Objects.requireNonNullElse(format, DEFAULT_DECIMAL_FORMAT); }
    public ScalingFormula getScalingFormula() { return formula; }
    public String format(double value) { return format == null ? String.valueOf(value) : format.format(value); }

    public static SkillParameter empty(String key) {
        return new SkillParameter(key, 0d, new NonScalingFormula(0d), null);
    }

    public static SkillParameter fromConfig(Object input, String key) {
        if (input == null) return empty(key);
        if (input instanceof Number number)
            return new SkillParameter(key, number.doubleValue(), new NonScalingFormula(number.doubleValue()), null);
        if (input instanceof ConfigObject config) return fromSection(config, key);
        if (input instanceof Map<?, ?> raw) {
            Map<String, Object> values = new LinkedHashMap<>();
            raw.forEach((mapKey, value) -> values.put(String.valueOf(mapKey), value));
            return fromSection(new MapConfigObject(key, values), key);
        }
        throw new IllegalArgumentException("Skill parameter must be a number or config section");
    }

    private static SkillParameter fromSection(ConfigObject config, String fallbackKey) {
        String key = config.getKey() == null || config.getKey().isBlank() ? fallbackKey : config.getKey();
        String name = config.getString("name", inferModifierName(key));
        double item = config.getDouble("item", 0d);
        ScalingFormula player = ScalingFormula.fromConfig(raw(config, "player"));
        DecimalFormat decimal = config.contains("format") ? new DecimalFormat(config.getString("format")) : null;
        return new SkillParameter(name, item, player, decimal);
    }

    private static Object raw(ConfigObject config, String key) {
        if (!config.contains(key)) return null;
        if (config instanceof MapConfigObject map) return map.asMap().get(key);
        try { return config.getObject(key); }
        catch (RuntimeException ignored) {
            String scalar = config.getString(key, null);
            if (scalar == null) return null;
            try { return Double.parseDouble(scalar); }
            catch (NumberFormatException notNumber) { return scalar; }
        }
    }

    private static String inferModifierName(String key) {
        String normalized = Objects.requireNonNullElse(key, "")
                .replace('-', ' ').replace('_', ' ').trim().toLowerCase(Locale.ROOT);
        StringBuilder output = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (!output.isEmpty()) output.append(' ');
            output.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return output.toString();
    }
}
