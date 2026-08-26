package vn.svframe.svframelib.script.util.expression.placeholder;

import vn.svframe.svframelib.script.util.expression.EvaluationException;
import vn.svframe.svframelib.script.variable.Variable;
import vn.svframe.svframelib.skill.SkillMetadata;

public class MythicLibVariablePlaceholder implements ExpressionPlaceholder {
    private final String fullVariableName;

    public MythicLibVariablePlaceholder(String fullVariableName) {
        this.fullVariableName = fullVariableName;
    }

    @Override
    public Double parse(SkillMetadata metadata) {
        Object value = metadata.getVariable(fullVariableName);
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof Variable<?> variable && variable.getStored() instanceof Number number) return number.doubleValue();
        throw new EvaluationException("Variable '" + fullVariableName + "' is not numeric: " +
                (value == null ? "null" : value.getClass().getSimpleName()));
    }
}
