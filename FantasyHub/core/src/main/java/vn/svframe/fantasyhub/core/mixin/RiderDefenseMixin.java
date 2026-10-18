package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import vn.svframe.fantasyhub.core.RiderRuntime;

/** Knockback only. Damage barriers use the shared Lib final-damage stage. */
@Mixin(LivingEntity.class)
abstract class RiderDefenseMixin {
  @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
  private void fantasyhub$knockback(double strength, double x, double z, CallbackInfo ci) {
    if (RiderRuntime.knockbackImmune((LivingEntity) (Object) this)) ci.cancel();
  }
}
