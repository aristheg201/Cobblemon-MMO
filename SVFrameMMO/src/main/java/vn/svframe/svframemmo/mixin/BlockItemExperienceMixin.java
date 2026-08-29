package vn.svframe.svframemmo.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(BlockItem.class)
public abstract class BlockItemExperienceMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void svframemmo$placed(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted() || !(context.getPlayer() instanceof ServerPlayerEntity player)
                || !(context.getWorld() instanceof ServerWorld world)) return;
        BlockPos pos = context.getBlockPos();
        BlockState state = world.getBlockState(pos);
        SVFrameMMO.nativeExperience().onBlockPlaced(player, world, pos, state);
    }
}
