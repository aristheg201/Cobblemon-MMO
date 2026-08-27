package vn.svframe.svframelib.skill.parameter.value;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.Map;

/** SVFrameLib 1.7.1 linear formula: base + per-level * (level - 1), optional clamp. */
public class LinearScalingFormula implements ScalingFormula {
    private final double base;
    private final double perLevel;
    private final double min;
    private final double max;
    private final boolean hasMin;
    private final boolean hasMax;
    private final boolean integer;

    public LinearScalingFormula(double base, double perLevel) {
        this.base = base;
        this.perLevel = perLevel;
        this.min = 0d;
        this.max = 0d;
        this.hasMin = false;
        this.hasMax = false;
        this.integer = false;
    }

    public LinearScalingFormula(double base, double perLevel, double min, double max) {
        this.base = base;
        this.perLevel = perLevel;
        this.min = min;
        this.max = max;
        this.hasMin = true;
        this.hasMax = true;
        this.integer = false;
    }

    public LinearScalingFormula(ConfigObject config) {
        this(config.getDouble("base", 0d),
                config.getDouble("per-level", config.getDouble("per_level", 0d)),
                config.contains("min"), config.getDouble("min", 0d),
                config.contains("max"), config.getDouble("max", 0d),
                config.getBoolean("int", false));
    }

    public LinearScalingFormula(Map<?, ?> config) {
        this(number(config.get("base"), 0d),
                number(first(config, "per-level", "per_level"), 0d),
                config.containsKey("min"), number(config.get("min"), 0d),
                config.containsKey("max"), number(config.get("max"), 0d),
                bool(config.get("int"), false));
    }

    private LinearScalingFormula(double base, double perLevel,
                                 boolean requestedMin, double min,
                                 boolean requestedMax, double max,
                                 boolean integer) {
        boolean invalidRange = requestedMin && requestedMax && min >= max;
        this.base = base;
        this.perLevel = perLevel;
        this.hasMin = requestedMin && !invalidRange;
        this.hasMax = requestedMax && !invalidRange;
        this.min = this.hasMin ? min : 0d;
        this.max = this.hasMax ? max : 0d;
        this.integer = integer;
    }

    @Override
    public double evaluate(int level, ServerPlayerEntity player) {
        double value = base + perLevel * (level - 1d);
        if (hasMin) value = Math.max(min, value);
        if (hasMax) value = Math.min(max, value);
        return value;
    }

    @Override
    public boolean isInteger() {
        return integer;
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean flag) return flag;
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
}
