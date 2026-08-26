package io.lumine.mythic.lib.script.util.expression.numeric;

import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.util.Lazy;

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
