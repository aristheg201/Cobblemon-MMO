package vn.svframe.svframelib.api.condition.type;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
public interface WorldCondition extends LocationCondition {
    boolean check(ServerWorld world);
    @Override default boolean check(ServerWorld world, BlockPos pos){return check(world);}
}
