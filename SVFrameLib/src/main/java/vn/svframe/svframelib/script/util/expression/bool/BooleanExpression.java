package vn.svframe.svframelib.script.util.expression.bool;

import vn.svframe.svframelib.script.util.expression.AbstractExpression;

import java.util.Map;

public abstract class BooleanExpression extends AbstractExpression {
    public static boolean eval(String expression) {
        try {
            return Math.abs(RUNTIME.evaluate(expression, Map.of())) > EPSILON;
        } catch (IllegalArgumentException numericFailure) {
            return RUNTIME.evaluateBoolean(expression, Map.of());
        }
    }
}
