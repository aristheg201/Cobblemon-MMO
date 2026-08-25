package io.lumine.mythic.lib.api.condition;
import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.condition.type.MMOCondition;
import io.lumine.mythic.lib.api.condition.type.PlayerCondition;
import net.minecraft.server.network.ServerPlayerEntity;
public class SneakingCondition extends MMOCondition implements PlayerCondition {
    public SneakingCondition(MMOLineConfig config){super(config);}
    @Override public boolean check(ServerPlayerEntity player){return player.isSneaking();}
}
