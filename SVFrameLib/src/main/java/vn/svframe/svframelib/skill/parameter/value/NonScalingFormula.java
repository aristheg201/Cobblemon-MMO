package vn.svframe.svframelib.skill.parameter.value;

import net.minecraft.server.network.ServerPlayerEntity;

/** Constant formula. SVFrameLib 1.7.1 does not mark constants as integer-only. */
public class NonScalingFormula implements ScalingFormula {
    private final double constant;

    public NonScalingFormula(double constant) {
        this.constant = constant;
    }

    @Override
    public double evaluate(int level, ServerPlayerEntity player) {
        return constant;
    }

    @Override
    public boolean isInteger() {
        return false;
    }
}
