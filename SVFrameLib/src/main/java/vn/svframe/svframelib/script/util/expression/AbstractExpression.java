package vn.svframe.svframelib.script.util.expression;

import vn.svframe.svframelib.fabric.runtime.script.ExpressionRuntime;

public abstract class AbstractExpression {
    protected static final ExpressionRuntime RUNTIME = new ExpressionRuntime();
    protected static final double EPSILON = 1.0E-10d;
}
