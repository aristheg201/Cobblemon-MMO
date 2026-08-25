package io.lumine.mythic.lib.skill.result.def;
import io.lumine.mythic.lib.skill.SkillMetadata; import io.lumine.mythic.lib.skill.result.SkillResult; import net.minecraft.util.math.Vec3d;
public class LocationSkillResult implements SkillResult { private final Vec3d target; public LocationSkillResult(SkillMetadata m){this.target=m.getTargetLocationOrNull();} public LocationSkillResult(SkillMetadata m,double ignored){this(m);} public LocationSkillResult(Vec3d target){this.target=target;} public Vec3d getTarget(){return target;} public boolean isSuccessful(){return target!=null;} }
