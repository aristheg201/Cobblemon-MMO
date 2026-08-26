package vn.svframe.svframelib.api.condition;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.condition.type.MMOCondition;
import net.minecraft.server.world.ServerWorld;
public class WorldCondition extends MMOCondition implements vn.svframe.svframelib.api.condition.type.WorldCondition {
    private final String world;
    public WorldCondition(MMOLineConfig config){super(config);world=config.getString("name");}
    @Override public boolean check(ServerWorld value){String full=value.getRegistryKey().getValue().toString();String path=value.getRegistryKey().getValue().getPath();return full.equals(world)||path.equals(world);}
}
