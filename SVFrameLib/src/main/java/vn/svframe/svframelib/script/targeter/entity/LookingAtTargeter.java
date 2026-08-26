package vn.svframe.svframelib.script.targeter.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.script.targeter.TargeterMath;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;
import java.util.UUID;

public class LookingAtTargeter implements EntityTargeter {
    private final NumericExpression range,size; private final boolean ignorePassable;
    public LookingAtTargeter(ConfigObject config){size=config.contains("size")?NumericExpression.compile(config.getString("size")):NumericExpression.of(.2d);range=config.contains("length")?NumericExpression.compile(config.getString("length")):NumericExpression.of(50d);ignorePassable=config.getBoolean("ignore_passable",true);}
    @Override public List<UUID> findTargets(SkillMetadata meta){double s=size.evaluate(meta),r=range.evaluate(meta);if(s<0)throw new IllegalArgumentException("Size must be positive");if(r<=0)throw new IllegalArgumentException("Length must be strictly positive");
        ServerPlayerEntity caster=meta.getCaster().getPlayer();ServerWorld world=(ServerWorld)caster.getWorld();Vec3d start=caster.getEyePos(),dir=caster.getRotationVec(1f).normalize(),end=start.add(dir.multiply(r));
        HitResult block=world.raycast(new RaycastContext(start,end,ignorePassable?RaycastContext.ShapeType.COLLIDER:RaycastContext.ShapeType.OUTLINE,RaycastContext.FluidHandling.NONE,caster));double max=r;
        if(block.getType()!=HitResult.Type.MISS)max=Math.min(max,start.distanceTo(block.getPos()));Vec3d clipped=start.add(dir.multiply(max));
        Box sweep=new Box(Math.min(start.x,clipped.x)-s,Math.min(start.y,clipped.y)-s,Math.min(start.z,clipped.z)-s,Math.max(start.x,clipped.x)+s,Math.max(start.y,clipped.y)+s,Math.max(start.z,clipped.z)+s);
        Entity best=null;double bestT=Double.POSITIVE_INFINITY;for(Entity e:world.getOtherEntities(caster,sweep,Entity::isAlive)){double t=TargeterMath.segmentBoxHit(start,clipped,e.getBoundingBox().expand(s));if(t<bestT){best=e;bestT=t;}}
        return best==null?List.of():List.of(best.getUuid());}
}
