package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(TameableEntity.class)
public abstract class TameableEntityExperienceMixin {
    @Inject(method = "setOwner", at = @At("TAIL"))
    private void svframemmo$tamed(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer)
            SVFrameMMO.nativeExperience().onTamed(serverPlayer, (TameableEntity) (Object) this);
    }
}
