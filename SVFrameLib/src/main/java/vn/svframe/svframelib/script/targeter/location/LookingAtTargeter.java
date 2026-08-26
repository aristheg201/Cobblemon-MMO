package vn.svframe.svframelib.script.targeter.location;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;

public class LookingAtTargeter extends LocationTargeter {
    private final double length; private final boolean ignorePassable;
    public LookingAtTargeter(ConfigObject config){super(false);length=config.getDouble("length",50d);ignorePassable=config.getBoolean("ignore_passable",true);}
    @Override public List<Vec3d> findTargets(SkillMetadata meta){ServerPlayerEntity caster=meta.getCaster().getPlayer();ServerWorld world=(ServerWorld)caster.getWorld();Vec3d start=caster.getEyePos(),end=start.add(caster.getRotationVec(1f).multiply(length));HitResult result=world.raycast(new RaycastContext(start,end,ignorePassable?RaycastContext.ShapeType.COLLIDER:RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,caster));return result.getType()==HitResult.Type.MISS?List.of():List.of(result.getPos());}
}
