package vn.svframe.fantasyhub.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;

/** Uses the same animated bone-local geometry in vanilla's actual first-person arm transform. */
public final class RiderFirstPersonArmor {
  public static void render(
      AbstractClientPlayerEntity player,
      PlayerEntityModel<?> model,
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      boolean left) {
    HeroArmorFeature.firstPerson(player,model,matrices,vertices,light,left);
    if(left==(player.getMainArm()==net.minecraft.util.Arm.LEFT))
      HeroEquipmentModels.held(matrices,vertices,light,model,player,0);
    var state = RiderVisuals.state(player.getId());
    if (state == null) return;
    RiderModels.render(
        state.form(),
        left ? "left_arm" : "right_arm",
        left ? model.leftArm : model.rightArm,
        matrices,
        vertices,
        light,
        state.force() == 6,
        p -> !p.get("name").getAsString().contains("shoulder"));
  }
}
