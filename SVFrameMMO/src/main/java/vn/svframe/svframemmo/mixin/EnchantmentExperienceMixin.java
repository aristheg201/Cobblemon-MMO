package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentExperienceMixin {
    @Inject(method = "onButtonClick", at = @At("RETURN"))
    private void svframemmo$enchanted(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue()) || !(player instanceof ServerPlayerEntity serverPlayer)) return;
        ItemStack stack = ((EnchantmentScreenHandler) (Object) this).getSlot(0).getStack();
        if (!stack.isEmpty()) SVFrameMMO.nativeExperience().onEnchanted(serverPlayer, stack.copy());
    }
}
