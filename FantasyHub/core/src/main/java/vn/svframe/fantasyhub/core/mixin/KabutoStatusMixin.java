package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.fantasyhub.core.KabutoRuntime;

@Mixin(LivingEntity.class)
abstract class KabutoStatusMixin {
  @Inject(
      method =
          "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
      at = @At("HEAD"),
      cancellable = true)
  private void fantasyhub$slowImmunity(
      StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> ci) {
    var target = (LivingEntity) (Object) this;
    if (effect.getEffectType().equals(StatusEffects.SLOWNESS) && KabutoRuntime.clock(target)
        || vn.svframe.fantasyhub.core.RiderRuntime.statusImmune(target, effect.getEffectType()))
      ci.setReturnValue(false);
  }
}
