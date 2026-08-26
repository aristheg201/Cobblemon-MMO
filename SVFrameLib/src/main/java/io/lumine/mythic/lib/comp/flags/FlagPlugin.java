package io.lumine.mythic.lib.comp.flags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
public interface FlagPlugin {
    boolean isPvpAllowed(ServerWorld world, BlockPos pos);
    boolean isFlagAllowed(ServerPlayerEntity player, CustomFlag flag);
    boolean isFlagAllowed(ServerWorld world, BlockPos pos, CustomFlag flag);
}
