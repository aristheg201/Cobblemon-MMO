package vn.svframe.svframelib.skill.result;
import vn.svframe.svframelib.skill.SkillMetadata;
public interface SkillResult { boolean isSuccessful(); default boolean isSuccessful(SkillMetadata metadata){return isSuccessful();} }
