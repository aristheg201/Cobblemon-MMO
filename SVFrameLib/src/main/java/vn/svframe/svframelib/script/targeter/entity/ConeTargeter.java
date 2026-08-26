package vn.svframe.svframelib.script.targeter.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.script.targeter.LocationTargeter;
import vn.svframe.svframelib.script.targeter.TargeterMath;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ConeTargeter implements EntityTargeter {
    private final NumericExpression radius, angle; private final LocationTargeter sourceLocation,direction;
    public ConeTargeter(ConfigObject config){config.validateKeys("radius","angle");sourceLocation=config.contains("source")?config.getLocationTargeter("source"):null;direction=config.contains("direction")?config.getLocationTargeter("direction"):null;angle=NumericExpression.compile(config.getString("angle"));radius=NumericExpression.compile(config.getString("radius"));}
    @Override public List<UUID> findTargets(SkillMetadata meta){ServerPlayerEntity caster=meta.getCaster().getPlayer();Vec3d loc=sourceLocation==null?caster.getEyePos():first(sourceLocation.findTargets(meta),"source");Vec3d dir=direction==null?caster.getRotationVec(1f):first(direction.findTargets(meta),"direction");
        double r=radius.evaluate(meta),a=Math.toRadians(angle.evaluate(meta));if(r<0)throw new IllegalArgumentException("Radius cannot be negative");ServerWorld world=(ServerWorld)caster.getWorld();Box box=new Box(loc.x-r,loc.y-r,loc.z-r,loc.x+r,loc.y+r,loc.z+r);List<UUID> out=new ArrayList<>();
        for(Entity e:world.getOtherEntities(null,box,Entity::isAlive))if(!e.getUuid().equals(caster.getUuid())&&TargeterMath.angle(e.getPos().subtract(loc),dir)<a)out.add(e.getUuid());return out;}
    private static Vec3d first(List<Vec3d> list,String key){if(list.isEmpty())throw new IllegalArgumentException("Targeter '"+key+"' returned no location");return list.getFirst();}
}
