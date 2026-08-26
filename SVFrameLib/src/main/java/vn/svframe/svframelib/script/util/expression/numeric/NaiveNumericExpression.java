package vn.svframe.svframelib.script.util.expression.numeric;

import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.Lazy;

public class NaiveNumericExpression extends NumericExpression {
    private final String expression;

    public NaiveNumericExpression(String expression) {
        this.expression = expression;
    }

    @Override
    public double evaluate(Lazy<SkillMetadata> metadata) {
        return eval(metadata.get().parseString(expression));
    }

    @Override
    public double evaluate(SkillMetadata metadata) {
        return eval(metadata.parseString(expression));
    }
}
