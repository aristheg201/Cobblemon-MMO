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
import vn.svframe.svframelib.fabric.SVFrameLibWorkbenchMod;

/** Owns SVFrameLib result-click semantics for custom and vanilla crafting stations. */
@Mixin(ScreenHandler.class)
public abstract class WorkbenchScreenMixin {
    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void svframelib$craftingClick(int slotIndex, int button, SlotActionType actionType,
                                         PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ScreenHandler handler = (ScreenHandler) (Object) this;
        if (SVFrameLibWorkbenchMod.handleClick(serverPlayer, handler, slotIndex, actionType)
                || SVFrameLibVanillaCraftingMod.handleClick(serverPlayer, handler, slotIndex, actionType)) {
            ci.cancel();
        }
    }

    @Inject(method = "onSlotClick", at = @At("RETURN"))
    private void svframelib$refreshVanillaCrafting(int slotIndex, int button, SlotActionType actionType,
                                                   PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity) SVFrameLibVanillaCraftingMod.refresh((ScreenHandler) (Object) this);
    }
}
