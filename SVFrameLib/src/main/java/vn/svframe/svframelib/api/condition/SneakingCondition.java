package vn.svframe.svframelib.api.condition;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.condition.type.MMOCondition;
import vn.svframe.svframelib.api.condition.type.PlayerCondition;
import net.minecraft.server.network.ServerPlayerEntity;
public class SneakingCondition extends MMOCondition implements PlayerCondition {
    public SneakingCondition(MMOLineConfig config){super(config);}
    @Override public boolean check(ServerPlayerEntity player){return player.isSneaking();}
}
