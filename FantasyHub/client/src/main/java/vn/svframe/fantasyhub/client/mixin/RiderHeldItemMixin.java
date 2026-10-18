package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.RiderWeapons;

@Mixin(HeldItemFeatureRenderer.class)
abstract class RiderHeldItemMixin {
  @Inject(
      method =
          "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
      at = @At("HEAD"),
      cancellable = true)
  private void fantasyhub$formHands(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      LivingEntity entity,
      float limb,
      float distance,
      float delta,
      float age,
      float yaw,
      float pitch,
      CallbackInfo ci) {
    if (RiderWeapons.overrides(entity.getId())) ci.cancel();
  }
}
