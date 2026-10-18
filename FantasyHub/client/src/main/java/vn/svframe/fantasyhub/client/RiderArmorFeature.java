package vn.svframe.fantasyhub.client;

import java.util.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

/** Real equipment, rendered slot by slot; removing a piece removes its geometry. */
public final class RiderArmorFeature
    extends FeatureRenderer<
        AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
  RiderArmorFeature(
      FeatureRendererContext<
              AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>
          context) {
    super(context);
  }

  public static String form(ItemStack stack) {
    var data = stack.get(DataComponentTypes.CUSTOM_DATA);
    if (data == null) return "";
    var texture = data.copyNbt().getString("SVFRAME_ARMOR_TEXTURE");
    if (!texture.startsWith("fantasyhub:riders/")) return "";
    String form = texture.substring("fantasyhub:riders/".length());
    return RiderModels.WEARABLE.contains(form) ? form : "";
  }

  static boolean boot(com.google.gson.JsonObject part) {
    String name = part.get("name").getAsString();
    return name.contains("boot_") || name.contains("ankle_");
  }

  @Override
  public void render(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      AbstractClientPlayerEntity player,
      float limbAngle,
      float limbDistance,
      float tickDelta,
      float age,
      float headYaw,
      float headPitch) {
    if (player.isInvisible() || KabutoVisuals.state(player.getId()) != null) return;
    var model = getContextModel();
    var active = RiderVisuals.state(player.getId());
    if (active != null) {
      for (String bone :
          List.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg")) {
        var part =
            switch (bone) {
              case "head" -> model.head;
              case "body" -> model.body;
              case "left_arm" -> model.leftArm;
              case "right_arm" -> model.rightArm;
              case "left_leg" -> model.leftLeg;
              default -> model.rightLeg;
            };
        RiderModels.render(
            active.form(), bone, part, matrices, vertices, light, active.force() == 6, p -> true);
      }
      RiderAttachments.render(player, model, matrices, vertices, light);
      return;
    }
    var physicalBelt = vn.svframe.fantasyhub.protocol.BeltEquipment.equippedAny(player);
    String beltForm =
        physicalBelt.isOf(vn.svframe.fantasyhub.protocol.Protocol.agitoBelt)
            ? "agito_ground"
            : physicalBelt.isOf(vn.svframe.fantasyhub.protocol.Protocol.zoldaBelt)
                ? "zolda"
                : physicalBelt.isOf(vn.svframe.fantasyhub.protocol.Protocol.ryukiBelt)
                    ? "ryuki"
                    : physicalBelt.isOf(vn.svframe.fantasyhub.protocol.Protocol.knightBelt)
                        ? "knight"
                        : physicalBelt.isOf(vn.svframe.fantasyhub.protocol.Protocol.decadeBelt)
                            ? "decade"
                            : "";
    if (!beltForm.isEmpty() && form(player.getEquippedStack(EquipmentSlot.CHEST)).isEmpty())
      RiderModels.render(
          beltForm,
          "body",
          model.body,
          matrices,
          vertices,
          light,
          false,
          p -> {
            String name = p.get("name").getAsString();
            return name.startsWith("belt_") || name.startsWith("waist_");
          });
    for (var slot :
        List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
      String form = form(player.getEquippedStack(slot));
      if (form.isEmpty()) continue;
      for (String bone :
          switch (slot) {
            case HEAD -> List.of("head");
            case CHEST -> List.of("body", "left_arm", "right_arm");
            case LEGS, FEET -> List.of("left_leg", "right_leg");
            default -> List.<String>of();
          }) {
        var part =
            switch (bone) {
              case "head" -> model.head;
              case "body" -> model.body;
              case "left_arm" -> model.leftArm;
              case "right_arm" -> model.rightArm;
              case "left_leg" -> model.leftLeg;
              default -> model.rightLeg;
            };
        RiderModels.render(
            form,
            bone,
            part,
            matrices,
            vertices,
            light,
            false,
            p ->
                slot != EquipmentSlot.LEGS && slot != EquipmentSlot.FEET
                    || boot(p) == (slot == EquipmentSlot.FEET));
      }
    }
  }

  public static void visibility(
      PlayerEntityModel<?> model, net.minecraft.entity.LivingEntity entity) {
    if (RiderVisuals.state(entity.getId()) != null) {
      model.head.visible = model.hat.visible = model.jacket.visible = false;
      model.leftSleeve.visible = model.rightSleeve.visible = false;
      model.leftPants.visible = model.rightPants.visible = false;
      return;
    }
    if (!form(entity.getEquippedStack(EquipmentSlot.HEAD)).isEmpty())
      model.head.visible = model.hat.visible = false;
    if (!form(entity.getEquippedStack(EquipmentSlot.CHEST)).isEmpty())
      model.jacket.visible = model.leftSleeve.visible = model.rightSleeve.visible = false;
    if (!form(entity.getEquippedStack(EquipmentSlot.LEGS)).isEmpty())
      model.leftPants.visible = model.rightPants.visible = false;
  }
}
