package vn.svframe.svframelib.script.targeter.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.List;
import java.util.UUID;

public class NearestEntityTargeter implements EntityTargeter {
    private final NumericExpression radius; private final boolean source;
    public NearestEntityTargeter(ConfigObject config){config.validateKeys("radius");source=config.getBoolean("source",false);radius=NumericExpression.compile(config.getString("radius"));}
    @Override public List<UUID> findTargets(SkillMetadata meta){Vec3d loc=meta.getSkillLocation(source);double r=radius.evaluate(meta);if(r<0)throw new IllegalArgumentException("Radius cannot be negative");
        ServerWorld world=(ServerWorld)meta.getCaster().getPlayer().getWorld();Box box=new Box(loc.x-r,loc.y-r,loc.z-r,loc.x+r,loc.y+r,loc.z+r);Entity nearest=null;double dist=Double.MAX_VALUE;
        for(Entity e:world.getOtherEntities(null,box,Entity::isAlive)){double d=e.getPos().squaredDistanceTo(loc);if(d<dist){nearest=e;dist=d;}}
        return nearest==null?List.of():List.of(nearest.getUuid());}
}
