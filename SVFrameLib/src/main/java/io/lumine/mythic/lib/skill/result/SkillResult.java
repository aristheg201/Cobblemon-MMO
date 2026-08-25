package io.lumine.mythic.lib.skill.result;
import io.lumine.mythic.lib.skill.SkillMetadata;
public interface SkillResult { boolean isSuccessful(); default boolean isSuccessful(SkillMetadata metadata){return isSuccessful();} }
