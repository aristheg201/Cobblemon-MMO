package vn.svframe.svframelib.fabric.runtime.skill;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.skill.parameter.value.FormulaFailsafeException;

/** Runtime adapter around the public SVFrameLib 1.7.1 scaling-formula API. */
public final class ScalingFormula {
    private final vn.svframe.svframelib.skill.parameter.value.ScalingFormula delegate;

    public ScalingFormula(double base, double perLevel, double min, double max) {
        this(new vn.svframe.svframelib.skill.parameter.value.LinearScalingFormula(base, perLevel, min, max));
    }

    private ScalingFormula(vn.svframe.svframelib.skill.parameter.value.ScalingFormula delegate) {
        this.delegate = delegate;
    }

    public static ScalingFormula fromConfig(Object input) {
        return new ScalingFormula(vn.svframe.svframelib.skill.parameter.value.ScalingFormula.fromConfig(input));
    }

    public static ScalingFormula fromConfig(Object input, ScalingFormula previous) {
        return new ScalingFormula(vn.svframe.svframelib.skill.parameter.value.ScalingFormula.fromConfig(
                input, previous == null ? null : previous.delegate));
    }

    public double evaluate(int level) {
        return evaluate(level, null);
    }

    public double evaluate(int level, ServerPlayerEntity player) {
        try {
            return delegate.evaluate(level, player);
        } catch (FormulaFailsafeException exception) {
            return exception.getFailsafe();
        }
    }

    public boolean isInteger() {
        return delegate.isInteger();
    }
}
