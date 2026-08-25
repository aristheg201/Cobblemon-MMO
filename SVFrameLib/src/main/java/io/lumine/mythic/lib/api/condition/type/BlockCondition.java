package io.lumine.mythic.lib.api.condition.type;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
public interface BlockCondition { boolean check(ServerWorld world, BlockPos pos); }
