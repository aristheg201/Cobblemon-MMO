package vn.svframe.svframelib.skill.result;
import vn.svframe.svframelib.skill.SkillMetadata;
public class MythicLibSkillResult implements SkillResult { private final boolean success; public MythicLibSkillResult(SkillMetadata metadata,String scriptId){this.success=scriptId!=null&&!scriptId.isBlank();} public MythicLibSkillResult(boolean success){this.success=success;} public boolean isSuccessful(){return success;} }
