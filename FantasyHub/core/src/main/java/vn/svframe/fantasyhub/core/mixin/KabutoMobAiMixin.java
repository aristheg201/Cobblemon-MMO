package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.KabutoRuntime;

/** Skip AI only while an active field owns the mob; never persistently set NoAI. */
@Mixin(MobEntity.class)
abstract class KabutoMobAiMixin {
  @Inject(method = "tickNewAi", at = @At("HEAD"), cancellable = true)
  private void fantasyhub$clock(CallbackInfo ci) {
    if (KabutoRuntime.frozen((MobEntity) (Object) this)) ci.cancel();
  }
}
