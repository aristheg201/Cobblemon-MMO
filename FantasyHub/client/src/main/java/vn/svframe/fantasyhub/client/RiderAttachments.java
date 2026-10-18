package vn.svframe.fantasyhub.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Models attach to the animated arm bones, including crouch and swing, without replacing inventory.
 */
final class RiderAttachments {
  static void render(
      AbstractClientPlayerEntity player,
      PlayerEntityModel<?> model,
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light) {
    var state = RiderVisuals.state(player.getId());
    if (state == null) return;
    if (state.form().equals("ryuki")) {
      attach("drag_visor", model.leftArm, matrices, vertices, light, 1, 6.1f, -.1f, .62f);
      if (RiderAnimator.posing(player.getId(), "PUNCH"))
        attach(
            state.force() == 1 ? "drag_visor_z" : "drag_claw",
            model.rightArm,
            matrices,
            vertices,
            light,
            -1,
            9,
            -.3f,
            .66f);
    }
    if (state.form().equals("decade") && RiderAnimator.posing(player.getId(), "SHIFT"))
      attach("ktouch", model.leftArm, matrices, vertices, light, 1, 9, -1.8f, .62f);
  }

  private static void attach(
      String name,
      ModelPart bone,
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      float x,
      float y,
      float z,
      float scale) {
    matrices.push();
    bone.rotate(matrices);
    matrices.translate(x / 16, y / 16, z / 16);
    matrices.scale(scale, scale, scale);
    RiderModels.localObject(name, matrices, vertices, light, 0xffffffff);
    matrices.pop();
  }
}
