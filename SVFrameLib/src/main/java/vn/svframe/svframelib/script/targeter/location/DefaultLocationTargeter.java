package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;

import java.util.List;

public class DefaultLocationTargeter extends LocationTargeter {
    public DefaultLocationTargeter() { super(false); }
    @Override public List<Vec3d> findTargets(SkillMetadata meta) { return List.of(meta.getSkillLocation(false)); }
}
