package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.CardRiderControl;

@Mixin(LivingEntity.class)
abstract class RiderControlMixin {
  @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
  private void fantasyhub$stun(CallbackInfo ci) {
    if (CardRiderControl.stunned((LivingEntity) (Object) this)||vn.svframe.fantasyhub.core.hero.HeroControl.rooted((LivingEntity)(Object)this)) ci.cancel();
  }

  @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
  private float fantasyhub$wound(float amount) {
    return CardRiderControl.healing((LivingEntity) (Object) this, amount);
  }
}
