package io.lumine.mythic.lib.api.condition;
import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.condition.type.MMOCondition;
import net.minecraft.server.world.ServerWorld;
public class WeatherCondition extends MMOCondition implements io.lumine.mythic.lib.api.condition.type.WorldCondition {
    public WeatherCondition(MMOLineConfig config){super(config);}
    @Override public boolean check(ServerWorld world){return world.isRaining();}
}
