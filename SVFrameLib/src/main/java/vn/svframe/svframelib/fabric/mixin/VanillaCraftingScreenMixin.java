package vn.svframe.svframelib.fabric.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframelib.fabric.SVFrameLibVanillaCraftingMod;

/** Routes SVFrameLib custom results through native Fabric vanilla station handlers. */
@Mixin(ScreenHandler.class)
public abstract class VanillaCraftingScreenMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void svframelib$vanillaCraftingClick(int slotIndex, int button, SlotActionType actionType,
                                                 PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer
                && SVFrameLibVanillaCraftingMod.handleClick(serverPlayer, (ScreenHandler) (Object) this, slotIndex, actionType)) {
            ci.cancel();
        }
    }
}
