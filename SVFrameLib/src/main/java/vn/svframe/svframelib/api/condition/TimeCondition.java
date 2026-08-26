package vn.svframe.svframelib.api.condition;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.condition.type.MMOCondition;
import net.minecraft.server.world.ServerWorld;
public class TimeCondition extends MMOCondition implements vn.svframe.svframelib.api.condition.type.WorldCondition {
    private final int minTime,maxTime;
    public TimeCondition(MMOLineConfig config){super(config);minTime=convertTime(config.getString("min"));maxTime=convertTime(config.getString("max"));}
    private int convertTime(String value){if(value.matches("\\d+"))return Math.min(24000,Math.max(0,Integer.parseInt(value)));return switch(value.toLowerCase()){case"day"->1000;case"noon"->6000;case"sunset"->12000;case"night"->13000;case"midnight"->18000;case"sunrise"->23000;default->-1;};}
    @Override public boolean check(ServerWorld world){long time=world.getTimeOfDay()%24000L;return time>minTime&&time<maxTime;}
}
