package vn.svframe.svframelib.skill.parameter.value;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.runtime.script.ExpressionRuntime;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.Map;
import java.util.Objects;

/** Custom expression-backed scaling formula with 1.7.1 failsafe/placeholder/int semantics. */
public class CustomScalingFormula implements ScalingFormula {
    private final String expression;
    private final double failsafe;
    private final boolean placeholders;
    private final boolean integer;
    private final ExpressionRuntime runtime = new ExpressionRuntime();

    public CustomScalingFormula(String expression) {
        this.expression = Objects.requireNonNull(expression, "Formula cannot be null");
        this.placeholders = expression.contains("%");
        this.integer = false;
        this.failsafe = 0d;
    }

    public CustomScalingFormula(ConfigObject config, ScalingFormula previous) {
        this.expression = Objects.requireNonNull(config.getString("formula"), "Formula cannot be null");
        this.placeholders = config.getBoolean("placeholders", true);
        this.integer = (previous != null && previous.isInteger()) || config.getBoolean("int", false);
        this.failsafe = config.getDouble("failsafe", 0d);
    }

    public CustomScalingFormula(Map<?, ?> config, ScalingFormula previous) {
        this.expression = Objects.requireNonNull(string(config.get("formula")), "Formula cannot be null");
        this.placeholders = bool(config.get("placeholders"), true);
        this.integer = (previous != null && previous.isInteger()) || bool(config.get("int"), false);
        this.failsafe = number(config.get("failsafe"), 0d);
    }

    @Override
    public double evaluate(int level, ServerPlayerEntity player) {
        try {
            String parsed = expression.replace("{level}", String.valueOf(level));
            if (placeholders && player != null) parsed = SVFrameLib.inst().getPlaceholderParser().parse(player, parsed);
            return runtime.evaluate(parsed, Map.of());
        } catch (RuntimeException exception) {
            throw new FormulaFailsafeException(exception, failsafe);
        }
    }

    @Override
    public boolean isInteger() {
        return integer;
    }

    public String getExpression() { return expression; }
    public double getFailsafe() { return failsafe; }
    public boolean usesPlaceholderParser() { return placeholders; }

    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
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
