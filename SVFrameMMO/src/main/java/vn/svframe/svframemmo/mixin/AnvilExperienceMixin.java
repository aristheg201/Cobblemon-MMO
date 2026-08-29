package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilExperienceMixin {
    @Inject(method = "onTakeOutput", at = @At("HEAD"))
    private void svframemmo$repaired(PlayerEntity player, ItemStack output, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        ItemStack input = ((AnvilScreenHandler) (Object) this).getSlot(0).getStack();
        if (!input.isEmpty()) SVFrameMMO.nativeExperience().onRepaired(serverPlayer, input.copy(), output.copy());
    }
}
