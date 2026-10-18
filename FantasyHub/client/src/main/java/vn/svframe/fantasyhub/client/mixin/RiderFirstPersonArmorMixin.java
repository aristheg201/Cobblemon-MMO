package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.RiderFirstPersonArmor;

@Mixin(PlayerEntityRenderer.class)
abstract class RiderFirstPersonArmorMixin {
  @Inject(method = "renderRightArm", at = @At("TAIL"))
  private void fantasyhub$right(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      AbstractClientPlayerEntity player,
      CallbackInfo ci) {
    RiderFirstPersonArmor.render(
        player,
        ((PlayerEntityRenderer) (Object) this).getModel(),
        matrices,
        vertices,
        light,
        false);
  }

  @Inject(method = "renderLeftArm", at = @At("TAIL"))
  private void fantasyhub$left(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      AbstractClientPlayerEntity player,
      CallbackInfo ci) {
    RiderFirstPersonArmor.render(
        player, ((PlayerEntityRenderer) (Object) this).getModel(), matrices, vertices, light, true);
  }
}
