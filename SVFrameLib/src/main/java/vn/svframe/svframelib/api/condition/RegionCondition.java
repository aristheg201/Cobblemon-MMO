package vn.svframe.svframelib.api.condition;
import vn.svframe.svframelib.api.MMOLineConfig;
import vn.svframe.svframelib.api.condition.type.MMOCondition;
import vn.svframe.svframelib.api.condition.type.LocationCondition;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import vn.svframe.svframelib.fabric.runtime.NativeRegionRegistry;
import java.util.*;
public class RegionCondition extends MMOCondition implements LocationCondition {
    private final Set<String> applicables;
    public RegionCondition(MMOLineConfig config){super(config);applicables=Set.copyOf(Arrays.asList(config.getString("name").split(",")));}
    @Override public boolean check(ServerWorld world,BlockPos pos){return NativeRegionRegistry.contains(world,pos,applicables);}
}
