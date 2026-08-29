package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.profession.fishing.CustomFishingRuntime;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberCustomFishingMixin {
    @Shadow private boolean caughtFish;

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void svframemmo$customFishing(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
        PlayerEntity owner = ((FishingBobberEntity) (Object) this).getPlayerOwner();
        if (!(owner instanceof ServerPlayerEntity player)) return;
        int result = CustomFishingRuntime.instance().onUse((FishingBobberEntity) (Object) this, player, usedItem, caughtFish);
        if (result != CustomFishingRuntime.PASS) cir.setReturnValue(result);
    }
}
