package vn.svframe.svframelib.script.variable.def;

import vn.svframe.svframelib.script.variable.SimpleVariableRegistry;
import vn.svframe.svframelib.script.variable.Variable;
import vn.svframe.svframelib.script.variable.VariableRegistry;

import java.util.Random;

public class RandomVariable extends Variable<Random> {
    public static final SimpleVariableRegistry<Random> VARIABLE_REGISTRY = new SimpleVariableRegistry<>();
    public static final RandomVariable INSTANCE = new RandomVariable();

    static {
        VARIABLE_REGISTRY.registerVariable("uniform", value -> new DoubleVariable("temp", value.nextDouble()), "unif", "double");
        VARIABLE_REGISTRY.registerVariable("gaussian", value -> new DoubleVariable("temp", value.nextGaussian()), "gauss");
        VARIABLE_REGISTRY.registerVariable("int", value -> new IntegerVariable("temp", value.nextInt()), "integer");
        VARIABLE_REGISTRY.registerVariable("bool", value -> new BooleanVariable("temp", value.nextBoolean()), "boolean");
    }

    private RandomVariable() {
        super("random", new Random());
    }

    /** Compatibility constructor retained for early Fabric callers. */
    public RandomVariable(String name, Random value) {
        super(name, value);
    }

    @Override
    public VariableRegistry<Variable<Random>> getVariableRegistry() {
        return VARIABLE_REGISTRY;
    }
}
