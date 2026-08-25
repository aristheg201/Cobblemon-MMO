package io.lumine.mythic.lib.api.condition;
import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.condition.type.MMOCondition;
import io.lumine.mythic.lib.api.condition.type.LocationCondition;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
public class BiomeCondition extends MMOCondition implements LocationCondition {
    private final String biome;
    public BiomeCondition(MMOLineConfig config){super(config);biome=config.getString("biome");}
    @Override public boolean check(ServerWorld world,BlockPos pos){return world.getBiome(pos).getKey().map(k->k.getValue().getPath().equalsIgnoreCase(biome)||k.getValue().toString().equalsIgnoreCase(biome)).orElse(false);}
}
