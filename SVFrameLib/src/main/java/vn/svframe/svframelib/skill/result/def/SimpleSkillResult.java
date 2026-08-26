package vn.svframe.svframelib.skill.result.def;
import vn.svframe.svframelib.skill.result.SkillResult;
public class SimpleSkillResult implements SkillResult { private final boolean success; public SimpleSkillResult(){this(true);} public SimpleSkillResult(boolean success){this.success=success;} public boolean isSuccessful(){return success;} }
