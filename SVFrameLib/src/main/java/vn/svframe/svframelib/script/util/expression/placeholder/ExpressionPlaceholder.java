package vn.svframe.svframelib.script.util.expression.placeholder;

import vn.svframe.svframelib.skill.SkillMetadata;

@FunctionalInterface
public interface ExpressionPlaceholder {
    Double parse(SkillMetadata metadata);
}
