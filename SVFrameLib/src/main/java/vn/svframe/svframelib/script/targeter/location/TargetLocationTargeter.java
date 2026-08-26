package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;

import java.util.List;

public class TargetLocationTargeter extends LocationTargeter {
    public TargetLocationTargeter() { super(false); }
    @Override public List<Vec3d> findTargets(SkillMetadata meta) { return List.of(meta.getTargetLocation()); }
}
