package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.protocol.Protocol;

/** KabutoEquipmentFeature renders the helmet only for a confirmed active form. */
@Mixin(HeadFeatureRenderer.class)
abstract class KabutoHeadMixin {
  @Inject(
      method =
          "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
      at = @At("HEAD"),
      cancellable = true)
  private void fantasyhub$head(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      LivingEntity entity,
      float limbAngle,
      float limbDistance,
      float tickDelta,
      float age,
      float yaw,
      float pitch,
      CallbackInfo ci) {
    if (entity instanceof PlayerEntity
        && (vn.svframe.fantasyhub.client.RiderVisuals.state(entity.getId()) != null
            || vn.svframe.fantasyhub.client.KabutoVisuals.state(entity.getId()) != null
            || !vn.svframe.fantasyhub.client.RiderArmorFeature.form(entity.getEquippedStack(EquipmentSlot.HEAD)).isEmpty()
            || entity.getEquippedStack(EquipmentSlot.HEAD).isOf(Protocol.kabutoHat))) ci.cancel();
  }
}
