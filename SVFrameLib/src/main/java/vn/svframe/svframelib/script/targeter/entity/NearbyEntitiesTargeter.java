package vn.svframe.svframelib.script.targeter.entity;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.script.targeter.EntityTargeter;
import vn.svframe.svframelib.script.util.expression.numeric.NumericExpression;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.util.configobject.ConfigObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NearbyEntitiesTargeter implements EntityTargeter {
    private final NumericExpression radius, height;
    private final boolean source, ignoreCaster;
    public NearbyEntitiesTargeter(ConfigObject config) {
        config.validateKeys("radius"); source=config.getBoolean("source",false); radius=NumericExpression.compile(config.getString("radius"));
        height=config.contains("height")?NumericExpression.compile(config.getString("height")):null; ignoreCaster=config.getBoolean("ignore_caster",true);
    }
    @Override public List<UUID> findTargets(SkillMetadata meta) {
        Vec3d loc=meta.getSkillLocation(source); double r=radius.evaluate(meta), h=height==null?r:height.evaluate(meta);
        if(r<0||h<0)throw new IllegalArgumentException("Radius/height cannot be negative");
        ServerWorld world=(ServerWorld)meta.getCaster().getPlayer().getWorld(); Box box=new Box(loc.x-r,loc.y-h,loc.z-r,loc.x+r,loc.y+h,loc.z+r);
        Entity ignored=ignoreCaster?meta.getSkillEntity(source):null; List<UUID> out=new ArrayList<>();
        for(Entity e:world.getOtherEntities(null,box,Entity::isAlive))if(ignored==null||!ignored.getUuid().equals(e.getUuid()))out.add(e.getUuid());
        return out;
    }
}
