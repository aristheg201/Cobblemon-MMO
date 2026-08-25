package io.lumine.mythic.lib.api.condition;
import io.lumine.mythic.lib.api.MMOLineConfig;
import io.lumine.mythic.lib.api.condition.type.MMOCondition;
import io.lumine.mythic.lib.api.condition.type.LocationCondition;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import vn.svframe.mythiclibfabric.runtime.NativeRegionRegistry;
import java.util.*;
public class RegionCondition extends MMOCondition implements LocationCondition {
    private final Set<String> applicables;
    public RegionCondition(MMOLineConfig config){super(config);applicables=Set.copyOf(Arrays.asList(config.getString("name").split(",")));}
    @Override public boolean check(ServerWorld world,BlockPos pos){return NativeRegionRegistry.contains(world,pos,applicables);}
}
