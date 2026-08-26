package vn.svframe.svframelib.script.variable.def;

import vn.svframe.svframelib.script.variable.*;
import net.minecraft.server.world.ServerWorld;

public class WorldVariable extends Variable<ServerWorld> {
    public static final SimpleVariableRegistry<ServerWorld> VARIABLE_REGISTRY = new SimpleVariableRegistry<>();
    static {
        VARIABLE_REGISTRY.registerVariable("time", w -> new IntegerVariable("temp", (int)(w.getTimeOfDay()%24000L)));
        VARIABLE_REGISTRY.registerVariable("name", w -> new StringVariable("temp", w.getRegistryKey().getValue().toString()));
    }
    public WorldVariable(String name, ServerWorld value){super(name,value);}
    @Override public VariableRegistry<Variable<ServerWorld>> getVariableRegistry(){return VARIABLE_REGISTRY;}
    @Override public String toString(){return getStored()==null?"None":getStored().getRegistryKey().getValue().toString();}
}
