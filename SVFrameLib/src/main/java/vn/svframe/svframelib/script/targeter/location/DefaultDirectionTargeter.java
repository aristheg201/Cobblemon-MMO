package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;

import java.util.List;

public class DefaultDirectionTargeter extends LocationTargeter {
    public DefaultDirectionTargeter() { super(false); }
    @Override public List<Vec3d> findTargets(SkillMetadata meta) { ServerPlayerEntity p = meta.getCaster().getPlayer(); return List.of(p.getEyePos().add(p.getRotationVec(1f))); }
}
