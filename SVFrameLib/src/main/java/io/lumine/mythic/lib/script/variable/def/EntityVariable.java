package io.lumine.mythic.lib.script.variable.def;

import io.lumine.mythic.lib.script.variable.*;
import io.lumine.mythic.lib.util.Position;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;

public class EntityVariable extends Variable<Entity> {
    public static final SimpleVariableRegistry<Entity> VARIABLE_REGISTRY = new SimpleVariableRegistry<>();
    static {
        VARIABLE_REGISTRY.registerVariable("id", e -> new IntegerVariable("temp",e.getId()));
        VARIABLE_REGISTRY.registerVariable("uuid", e -> new StringVariable("temp",e.getUuidAsString()));
        VARIABLE_REGISTRY.registerVariable("type", e -> new StringVariable("temp",Registries.ENTITY_TYPE.getId(e.getType()).toString()));
        VARIABLE_REGISTRY.registerVariable("location", e -> new PositionVariable("temp",new Position((ServerWorld)e.getWorld(),e.getPos())));
        VARIABLE_REGISTRY.registerVariable("bb_center", e -> new PositionVariable("temp",new Position((ServerWorld)e.getWorld(),e.getBoundingBox().getCenter())));
        VARIABLE_REGISTRY.registerVariable("eye_location", e -> new PositionVariable("temp",new Position((ServerWorld)e.getWorld(),e.getEyePos())));
        VARIABLE_REGISTRY.registerVariable("health", e -> new DoubleVariable("temp",e instanceof LivingEntity l?l.getHealth():0d));
        VARIABLE_REGISTRY.registerVariable("looking", e -> new PositionVariable("temp",new Position((ServerWorld)e.getWorld(),e.getRotationVec(1f))));
        VARIABLE_REGISTRY.registerVariable("velocity", e -> new PositionVariable("temp",new Position((ServerWorld)e.getWorld(),e.getVelocity())));
        VARIABLE_REGISTRY.registerVariable("height", e -> new DoubleVariable("temp",e.getHeight()));
        VARIABLE_REGISTRY.registerVariable("attribute", e -> new AttributesVariable("temp", e instanceof LivingEntity l ? l : null));
        VARIABLE_REGISTRY.registerVariable("fire_ticks", e -> new IntegerVariable("temp",e.getFireTicks()));
    }
    public EntityVariable(String name,Entity value){super(name,value);}
    @Override public VariableRegistry<Variable<Entity>> getVariableRegistry(){return VARIABLE_REGISTRY;}
    @Override public String toString(){return getStored()==null?"None":Integer.toString(getStored().getId());}
}
