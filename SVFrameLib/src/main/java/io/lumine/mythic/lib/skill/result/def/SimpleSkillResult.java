package io.lumine.mythic.lib.skill.result.def;
import io.lumine.mythic.lib.skill.result.SkillResult;
public class SimpleSkillResult implements SkillResult { private final boolean success; public SimpleSkillResult(){this(true);} public SimpleSkillResult(boolean success){this.success=success;} public boolean isSuccessful(){return success;} }
