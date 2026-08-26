package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;

import java.util.List;

public class ConstantLocationTargeter extends LocationTargeter {
    private final Vec3d value;
    public ConstantLocationTargeter(double x, double y, double z) { super(false); value = new Vec3d(x,y,z); }
    @Override public List<Vec3d> findTargets(SkillMetadata meta) { return List.of(value); }
}
