package vn.svframe.svframelib.script.variable.def;

import vn.svframe.svframelib.MythicLib;
import vn.svframe.svframelib.script.variable.SimpleVariableRegistry;
import vn.svframe.svframelib.script.variable.Variable;
import vn.svframe.svframelib.script.variable.VariableRegistry;
import vn.svframe.svframelib.util.lang3.Validate;

import java.text.DecimalFormat;

public class DoubleVariable extends Variable<Double> {
    public static final SimpleVariableRegistry<Double> VARIABLE_REGISTRY = new SimpleVariableRegistry<>();

    static {
        VARIABLE_REGISTRY.registerVariable("int", value -> new IntegerVariable("temp", value.intValue()));
        VARIABLE_REGISTRY.registerVariable("round", value -> new Round("temp", value));
    }

    public DoubleVariable(String name, double value) {
        super(name, value);
    }

    public DoubleVariable(String name, Double value) {
        this(name, value.doubleValue());
    }

    @Override
    public VariableRegistry<Variable<Double>> getVariableRegistry() {
        return VARIABLE_REGISTRY;
    }

    public static class Round extends Variable<Double> {
        public static final VariableRegistry<Variable<Double>> VARIABLE_REGISTRY = new VariableRegistry<>() {
            @Override
            public Variable<?> accessVariable(Variable<Double> variable, String path) {
                double value = variable.getStored();
                int decimalPlaces = Integer.parseInt(path);
                if (decimalPlaces == 0) return new IntegerVariable("temp", (int) value);
                Validate.isTrue(decimalPlaces > 0, "Decimal places must be non-negative");
                DecimalFormat format = MythicLib.plugin.getMMOConfig().newDecimalFormat("0." + "0".repeat(decimalPlaces));
                return new StringVariable("temp", format.format(value));
            }
        };

        public Round(String name, double value) {
            super(name, value);
        }

        @Override
        public VariableRegistry<Variable<Double>> getVariableRegistry() {
            return VARIABLE_REGISTRY;
        }
    }
}
