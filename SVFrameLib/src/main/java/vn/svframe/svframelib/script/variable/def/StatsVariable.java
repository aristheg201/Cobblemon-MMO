package vn.svframe.svframelib.script.variable.def;

import vn.svframe.svframelib.api.stat.provider.StatProvider;
import vn.svframe.svframelib.script.variable.*;

public class StatsVariable extends Variable<StatProvider> {
    public static final VariableRegistry<Variable<StatProvider>> VARIABLE_REGISTRY = (variable,path) -> {
        if(path==null||path.isBlank())return variable;
        return new DoubleVariable("temp", variable.getStored().getStat(path));
    };
    public StatsVariable(String name, StatProvider value){super(name,value);}
    @Override public VariableRegistry<Variable<StatProvider>> getVariableRegistry(){return VARIABLE_REGISTRY;}
    @Override public String toString(){return getStored()==null?"None":"StatProvider";}
}
