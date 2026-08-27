package vn.svframe.svframelib.skill.result;
import vn.svframe.svframelib.skill.SkillMetadata;
public class SVFrameLibSkillResult implements SkillResult { private final boolean success; public SVFrameLibSkillResult(SkillMetadata metadata,String scriptId){this.success=scriptId!=null&&!scriptId.isBlank();} public SVFrameLibSkillResult(boolean success){this.success=success;} public boolean isSuccessful(){return success;} }
