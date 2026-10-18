package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import vn.svframe.fantasyhub.client.RiderWeapons;

@Mixin(HeldItemRenderer.class)
abstract class RiderFirstPersonMixin {
  @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true)
  private ItemStack fantasyhub$formWeapon(
      ItemStack original,
      AbstractClientPlayerEntity p,
      float delta,
      float pitch,
      Hand hand,
      float swing,
      ItemStack stack,
      float equip,
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light) {
    return RiderWeapons.overrides(p.getId()) ? RiderWeapons.projected(p.getId(), hand) : original;
  }
}
