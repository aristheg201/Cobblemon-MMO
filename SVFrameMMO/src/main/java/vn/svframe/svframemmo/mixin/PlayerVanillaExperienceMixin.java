package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.experience.vanilla.VanillaProgressionRuntime;

@Mixin(PlayerEntity.class)
public abstract class PlayerVanillaExperienceMixin {
    @Inject(method = "addExperience", at = @At("TAIL"))
    private void svframemmo$vanillaExperience(int amount, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayerEntity player)
            VanillaProgressionRuntime.instance().onVanillaExperience(player, amount);
    }
}
