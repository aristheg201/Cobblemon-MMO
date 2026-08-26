package io.lumine.mythic.lib.script.util.expression.bool;

import io.lumine.mythic.lib.script.util.expression.AbstractExpression;

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
