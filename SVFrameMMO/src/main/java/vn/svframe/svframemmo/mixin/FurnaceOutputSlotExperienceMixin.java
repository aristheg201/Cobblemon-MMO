package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.FurnaceOutputSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(FurnaceOutputSlot.class)
public abstract class FurnaceOutputSlotExperienceMixin {
    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void svframemmo$smelted(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) SVFrameMMO.nativeExperience().onSmelted(serverPlayer, stack.copy());
    }
}
