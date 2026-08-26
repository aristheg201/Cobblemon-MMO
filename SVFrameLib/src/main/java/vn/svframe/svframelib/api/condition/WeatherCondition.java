package vn.svframe.svframelib.api.condition;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.condition.type.MMOCondition;
import net.minecraft.server.world.ServerWorld;
public class WeatherCondition extends MMOCondition implements vn.svframe.svframelib.api.condition.type.WorldCondition {
    public WeatherCondition(MMOLineConfig config){super(config);}
    @Override public boolean check(ServerWorld world){return world.isRaining();}
}
