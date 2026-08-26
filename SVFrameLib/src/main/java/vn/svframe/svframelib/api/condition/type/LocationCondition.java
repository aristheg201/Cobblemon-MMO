package vn.svframe.svframelib.api.condition.type;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
public interface LocationCondition extends PlayerCondition, BlockCondition {
    boolean check(ServerWorld world, BlockPos pos);
    @Override default boolean check(ServerPlayerEntity player){return check(player.getServerWorld(),player.getBlockPos());}
}
