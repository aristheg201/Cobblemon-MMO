package io.lumine.mythic.lib.script.util.expression.placeholder;

import io.lumine.mythic.lib.skill.SkillMetadata;

@FunctionalInterface
public interface ExpressionPlaceholder {
    Double parse(SkillMetadata metadata);
}
