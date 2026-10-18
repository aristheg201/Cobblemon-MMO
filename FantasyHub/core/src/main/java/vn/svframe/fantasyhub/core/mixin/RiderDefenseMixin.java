package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import vn.svframe.fantasyhub.core.RiderRuntime;

/** Final applied damage consumes only this Rider's barrier; native armor remains authoritative. */
@Mixin(LivingEntity.class)
abstract class RiderDefenseMixin {
  @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
  private void fantasyhub$barrier(
      DamageSource source, float amount, CallbackInfoReturnable<Float> ci) {
    var target = (LivingEntity) (Object) this;
    if (!target.getWorld().isClient)
      ci.setReturnValue(RiderRuntime.incoming(target, source, ci.getReturnValue()));
  }

  @Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
  private void fantasyhub$knockback(double strength, double x, double z, CallbackInfo ci) {
    if (RiderRuntime.controlImmune((LivingEntity) (Object) this)) ci.cancel();
  }
}
