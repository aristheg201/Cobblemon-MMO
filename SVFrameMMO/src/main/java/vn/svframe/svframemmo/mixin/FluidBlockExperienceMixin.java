package vn.svframe.svframemmo.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.profession.mining.CustomMiningRuntime;

/** Marks fluid-generator outputs as player-placed when MMOCore-compatible generator EXP is disabled. */
@Mixin(FluidBlock.class)
public abstract class FluidBlockExperienceMixin {
    @Inject(method = "receiveNeighborFluids", at = @At("RETURN"))
    private void svframemmo$generatorOutput(World world, BlockPos pos, BlockState originalState,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !(world instanceof ServerWorld serverWorld)
                || SVFrameMMO.config().shouldCobblestoneGeneratorsGiveExp()) return;

        BlockState formed = serverWorld.getBlockState(pos);
        if (!formed.isOf(Blocks.COBBLESTONE) && !formed.isOf(Blocks.OBSIDIAN) && !formed.isOf(Blocks.BASALT)) return;

        SVFrameMMO.nativeExperience().markPlayerPlaced(serverWorld, pos);
        CustomMiningRuntime.instance().markPlaced(serverWorld, pos);
    }
}
