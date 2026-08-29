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
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberExperienceMixin {
    @Shadow private boolean caughtFish;

    @Inject(method = "use", at = @At("HEAD"))
    private void svframemmo$fish(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
        if (!caughtFish) return;
        PlayerEntity owner = ((FishingBobberEntity) (Object) this).getPlayerOwner();
        if (owner instanceof ServerPlayerEntity serverPlayer) SVFrameMMO.nativeExperience().onFishCaught(serverPlayer, ItemStack.EMPTY);
    }
}
