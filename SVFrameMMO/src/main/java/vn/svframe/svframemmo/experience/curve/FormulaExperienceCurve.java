package vn.svframe.svframemmo.experience.curve;

import vn.svframe.svframelib.skill.parameter.value.FormulaFailsafeException;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Objects;

public final class FormulaExperienceCurve implements ExperienceCurve {
    private final String expression;
    private final ScalingFormula formula;

    public FormulaExperienceCurve(String expression) {
        this.expression = Objects.requireNonNull(expression, "Experience formula cannot be null");
        this.formula = ScalingFormula.fromConfig(expression);
    }

    @Override
    public long getExperience(PlayerData player, int level) {
        if (level <= 0) throw new IllegalArgumentException("Level must be strictly positive");
        try {
            long value = (long) formula.evaluate(level, player == null ? null : player.getPlayer());
            if (value <= 0) throw new IllegalArgumentException("Experience curve must return a positive value, got " + value);
            return value;
        } catch (FormulaFailsafeException exception) {
            return 100L;
        } catch (RuntimeException exception) {
            return 100L;
        }
    }

    public String getExpression() { return expression; }
}
