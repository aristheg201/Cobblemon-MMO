package vn.svframe.svframelib.skill.result.def;
import vn.svframe.svframelib.skill.SkillMetadata; import vn.svframe.svframelib.skill.result.SkillResult; import net.minecraft.entity.LivingEntity;
public class TargetSkillResult implements SkillResult { private final LivingEntity target; public TargetSkillResult(SkillMetadata m){this.target=m.getTargetLivingEntityOrNull();} public TargetSkillResult(LivingEntity target){this.target=target;} public LivingEntity getTarget(){return target;} public boolean isSuccessful(){return target!=null;} }
