package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.*;
import net.minecraft.util.math.RotationAxis;

/** Uses vanilla held-item transforms on the already animated player arm bones. */
final class RiderWeaponFeature
    extends FeatureRenderer<
        AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
  RiderWeaponFeature(
      FeatureRendererContext<
              AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>
          context) {
    super(context);
  }

  @Override
  public void render(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      AbstractClientPlayerEntity p,
      float limb,
      float distance,
      float delta,
      float age,
      float yaw,
      float pitch) {
    if (p.isInvisible() || !RiderWeapons.overrides(p.getId())) return;
    for (Hand hand : Hand.values()) {
      var stack = RiderWeapons.projected(p.getId(), hand);
      if (stack.isEmpty()) continue;
      Arm arm = hand == Hand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
      boolean left = arm == Arm.LEFT;
      matrices.push();
      getContextModel().setArmAngle(arm, matrices);
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
      matrices.translate((left ? -1 : 1) / 16d, .125, -.625);
      MinecraftClient.getInstance()
          .getEntityRenderDispatcher()
          .getHeldItemRenderer()
          .renderItem(
              p,
              stack,
              left
                  ? ModelTransformationMode.THIRD_PERSON_LEFT_HAND
                  : ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
              left,
              matrices,
              vertices,
              light);
      matrices.pop();
    }
  }
}
