package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.KabutoVisuals;

/** Pure presentation, activated only by a server-confirmed semantic cue. */
@Mixin(PlayerEntityModel.class)
abstract class KabutoPoseMixin {
  @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
  private void fantasyhub$pose(
      LivingEntity entity,
      float limbAngle,
      float limbDistance,
      float age,
      float headYaw,
      float headPitch,
      CallbackInfo ci) {
    KabutoVisuals.animate((PlayerEntityModel<?>) (Object) this, entity, age);
    vn.svframe.fantasyhub.client.RiderArmorFeature.visibility((PlayerEntityModel<?>) (Object)this,entity);
  }
}
