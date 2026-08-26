package vn.svframe.svframelib.skill.result.def;
import vn.svframe.svframelib.skill.SkillMetadata; import vn.svframe.svframelib.skill.result.SkillResult; import net.minecraft.util.math.Vec3d;
public class LocationSkillResult implements SkillResult { private final Vec3d target; public LocationSkillResult(SkillMetadata m){this.target=m.getTargetLocationOrNull();} public LocationSkillResult(SkillMetadata m,double ignored){this(m);} public LocationSkillResult(Vec3d target){this.target=target;} public Vec3d getTarget(){return target;} public boolean isSuccessful(){return target!=null;} }
