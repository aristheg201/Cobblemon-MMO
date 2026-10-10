package vn.svframe.svframelib.fabric.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframelib.fabric.HeroEntranceRuntime;

@Mixin(LivingEntity.class)
public abstract class HeroEntranceFallMixin {
    @Inject(method = "handleFallDamage", at = @At("HEAD"), cancellable = true)
    private void svframe$heroFall(float distance, float multiplier, DamageSource source,
                                  CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof ServerPlayerEntity player && HeroEntranceRuntime.protectsFall(player))
            callback.setReturnValue(false);
    }
}
