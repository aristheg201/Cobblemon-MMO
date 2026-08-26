package io.lumine.mythic.lib.script.variable.def;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.provider.StatProvider;
import io.lumine.mythic.lib.script.variable.*;
import io.lumine.mythic.lib.util.Position;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class PlayerVariable extends Variable<ServerPlayerEntity> {
    public static final SimpleVariableRegistry<ServerPlayerEntity> VARIABLE_REGISTRY = new SimpleVariableRegistry<>(EntityVariable.VARIABLE_REGISTRY);
    static {
        VARIABLE_REGISTRY.registerVariable("stat", p -> new StatsVariable("temp", StatProvider.get(p)));
        VARIABLE_REGISTRY.registerVariable("cooldown", p -> new CooldownsVariable("temp",MMOPlayerData.get(p).getCooldownMap()));
        VARIABLE_REGISTRY.registerVariable("name", p -> new StringVariable("temp",p.getName().getString()));
        VARIABLE_REGISTRY.registerVariable("eye_direction", p -> new PositionVariable("temp",new Position((ServerWorld)p.getWorld(),p.getRotationVec(1f))));
    }
    public PlayerVariable(String name,ServerPlayerEntity value){super(name,value);}
    @Override public VariableRegistry<Variable<ServerPlayerEntity>> getVariableRegistry(){return VARIABLE_REGISTRY;}
    @Override public String toString(){return getStored()==null?"None":getStored().getName().getString();}
}
