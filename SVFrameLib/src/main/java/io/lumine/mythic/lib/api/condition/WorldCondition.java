package io.lumine.mythic.lib.api.condition;
import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.condition.type.MMOCondition;
import net.minecraft.server.world.ServerWorld;
public class WorldCondition extends MMOCondition implements io.lumine.mythic.lib.api.condition.type.WorldCondition {
    private final String world;
    public WorldCondition(MMOLineConfig config){super(config);world=config.getString("name");}
    @Override public boolean check(ServerWorld value){String full=value.getRegistryKey().getValue().toString();String path=value.getRegistryKey().getValue().getPath();return full.equals(world)||path.equals(world);}
}
