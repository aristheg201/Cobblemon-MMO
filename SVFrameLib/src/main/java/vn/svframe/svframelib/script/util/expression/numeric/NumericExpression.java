package vn.svframe.svframelib.script.util.expression.numeric;

import vn.svframe.svframelib.script.util.expression.placeholder.ExpressionPlaceholder;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.Lazy;
import vn.svframe.svframelib.fabric.runtime.script.ExpressionRuntime;

import java.util.Map;
import java.util.function.Function;

public abstract class NumericExpression {
    public static NumericExpression ZERO = of(0d);
    public static NumericExpression ONE = of(1d);
    protected static final double BOOLEAN_EPSILON = 1.0E-10d;
    protected static final ExpressionRuntime RUNTIME = new ExpressionRuntime();

    public abstract double evaluate(SkillMetadata metadata);
    public abstract double evaluate(Lazy<SkillMetadata> metadata);

    public static double eval(String expression) {
        return RUNTIME.evaluate(expression, Map.of());
    }

    public static boolean evalBoolean(String expression) {
        return eval(expression) > BOOLEAN_EPSILON;
    }

    public static NumericExpression compile(String expression, Function<String, ExpressionPlaceholder> customPlaceholderResolver) {
        return new PrecompiledNumericExpression(expression, customPlaceholderResolver);
    }

    public static NumericExpression compile(String expression) {
        try {
            return new ConstantNumericExpression(Double.parseDouble(expression));
        } catch (NumberFormatException ignored) {
            try {
                return new PrecompiledNumericExpression(expression, null);
            } catch (Exception ignoredCompilationFailure) {
                return new NaiveNumericExpression(expression);
            }
        }
    }

    public static NumericExpression of(double value) {
        return new ConstantNumericExpression(value);
    }
}
