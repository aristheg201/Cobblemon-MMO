package vn.svframe.svframemmo.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.profession.mining.CustomMiningRuntime;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow protected ServerPlayerEntity player;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void svframemmo$customMiningHead(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        CustomMiningRuntime.BreakDecision decision = CustomMiningRuntime.instance().beforeBreak(player, pos);
        if (decision == CustomMiningRuntime.BreakDecision.DENY) cir.setReturnValue(false);
        else if (decision == CustomMiningRuntime.BreakDecision.HANDLED) cir.setReturnValue(true);
    }

    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void svframemmo$customMiningReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        CustomMiningRuntime.instance().afterVanillaBreak(player, pos, cir.getReturnValueZ());
    }
}
