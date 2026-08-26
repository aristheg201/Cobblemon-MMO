package vn.svframe.svframelib.script.util.expression.numeric;

import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.Lazy;

public class ConstantNumericExpression extends NumericExpression {
    private final double constantValue;
    public static NumericExpression ZERO = new ConstantNumericExpression(0d);
    public static NumericExpression ONE = new ConstantNumericExpression(1d);

    public ConstantNumericExpression(double constantValue) {
        this.constantValue = constantValue;
    }

    @Override
    public double evaluate(SkillMetadata metadata) {
        return constantValue;
    }

    @Override
    public double evaluate(Lazy<SkillMetadata> metadata) {
        return constantValue;
    }
}
