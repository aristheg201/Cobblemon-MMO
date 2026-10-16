package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import vn.svframe.fantasyhub.protocol.*;

/** Authored photographic-reference forms follow actual animated biped bones. */
final class KabutoEquipmentFeature
    extends FeatureRenderer<
        AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
  private static JsonObject forms;

  private record Vertex(
      float x, float y, float z, float u, float v, float nx, float ny, float nz) {}

  private static final Map<JsonObject, List<Vertex>> GEOMETRY = new IdentityHashMap<>();
  private net.minecraft.client.render.entity.model.BipedEntityModel<AbstractClientPlayerEntity>
      suit;
  private net.minecraft.client.render.entity.model.BipedEntityModel<AbstractClientPlayerEntity>
      originalOuter, originalLegs;

  KabutoEquipmentFeature(
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
      AbstractClientPlayerEntity player,
      float limbAngle,
      float limbDistance,
      float tickDelta,
      float age,
      float headYaw,
      float headPitch) {
    if (player.isInvisible()) return;
    Protocol.Kabuto state = KabutoVisuals.state(player.getId());
    ItemStack belt = BeltEquipment.equipped(player);
    if (!belt.isEmpty()) renderBelt(matrices, vertices, light, player, belt, state);
    String form = state != null && state.form() == 0 ? "masked" : "rider";
    var model = getContextModel();
    if (state != null) {
      if (suit == null)
        suit =
            new net.minecraft.client.render.entity.model.BipedEntityModel<>(
                net.minecraft.client.model.TexturedModelData.of(
                        net.minecraft.client.render.entity.model.BipedEntityModel.getModelData(
                            new net.minecraft.client.model.Dilation(.1f), 0),
                        64,
                        32)
                    .createModel());
      model.copyBipedStateTo(suit);
      suit.setVisible(true);
      suit.head.visible = suit.hat.visible = false;
      if (form.equals("masked")) suit.render(
          matrices,
          vertices.getBuffer(
              RenderLayer.getEntityCutoutNoCull(
                  Identifier.of("fantasyhub", "textures/author/kabuto_undersuit.png"))),
          light,
          OverlayTexture.DEFAULT_UV,
          0xffffffff);
      if (form.equals("rider")) {
        renderOriginalArmor(model, matrices, vertices, light);
        matrices.push();
        model.head.rotate(matrices);
        HeadFeatureRenderer.translate(matrices, false);
        MinecraftClient.getInstance()
            .getItemRenderer()
            .renderItem(
                new ItemStack(Protocol.kabutoHat),
                ModelTransformationMode.HEAD,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                vertices,
                player.getWorld(),
                player.getId());
        matrices.pop();
      }
    }
    if (state != null && form.equals("masked"))
      renderParts(form, "head", model.head, matrices, vertices, light);
    if (state != null && form.equals("masked")) {
      renderParts(form, "body", model.body, matrices, vertices, light);
      renderParts(form, "left_arm", model.leftArm, matrices, vertices, light);
      renderParts(form, "right_arm", model.rightArm, matrices, vertices, light);
    }
    if (state != null && form.equals("masked")) {
      renderParts(form, "left_leg", model.leftLeg, matrices, vertices, light);
      renderParts(form, "right_leg", model.rightLeg, matrices, vertices, light);
    }
  }

  /** Rider and Hyper use the unchanged armor textures from the user's original Kabuto archive. */
  private void renderOriginalArmor(PlayerEntityModel<AbstractClientPlayerEntity> source,
      MatrixStack matrices, VertexConsumerProvider vertices, int light) {
    if (originalOuter == null) {
      originalOuter = originalArmor(.5f);
      originalLegs = originalArmor(.25f);
    }
    source.copyBipedStateTo(originalLegs);
    originalLegs.setVisible(false);
    originalLegs.body.visible = originalLegs.leftLeg.visible = originalLegs.rightLeg.visible = true;
    originalLegs.render(matrices, vertices.getBuffer(RenderLayer.getArmorCutoutNoCull(
        Identifier.of("fantasyhub", "textures/armor/additions/kabuto_layer_2.png"))),
        light, OverlayTexture.DEFAULT_UV, 0xffffffff);
    source.copyBipedStateTo(originalOuter);
    originalOuter.setVisible(false);
    originalOuter.body.visible = originalOuter.leftArm.visible = originalOuter.rightArm.visible = true;
    originalOuter.leftLeg.visible = originalOuter.rightLeg.visible = true;
    originalOuter.render(matrices, vertices.getBuffer(RenderLayer.getArmorCutoutNoCull(
        Identifier.of("fantasyhub", "textures/armor/additions/kabuto_layer_1.png"))),
        light, OverlayTexture.DEFAULT_UV, 0xffffffff);
  }

  private static net.minecraft.client.render.entity.model.BipedEntityModel<AbstractClientPlayerEntity> originalArmor(float dilation) {
    return new net.minecraft.client.render.entity.model.BipedEntityModel<>(
        net.minecraft.client.model.TexturedModelData.of(
            net.minecraft.client.render.entity.model.BipedEntityModel.getModelData(
                new net.minecraft.client.model.Dilation(dilation), 0), 64, 32).createModel());
  }

  private void renderBelt(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light,
      AbstractClientPlayerEntity player,
      ItemStack belt,
      Protocol.Kabuto state) {
    var body = getContextModel().body;
    // A pale silver strap wraps the complete waist, with a separate rear clasp.
    for (float[] cube :
        new float[][] {
          {-4.35f, 9.6f, -2.65f, 8.7f, 1.9f, .6f},
          {-4.35f, 9.6f, 2.05f, 8.7f, 1.9f, .6f},
          {-4.5f, 9.6f, -2.65f, .65f, 1.9f, 5.3f},
          {3.85f, 9.6f, -2.65f, .65f, 1.9f, 5.3f},
          {-1.35f, 9.4f, 2.65f, 2.7f, 2.3f, .45f}
        }) {
      matrices.push();
      body.rotate(matrices);
      cube(
          matrices,
          vertices,
          Identifier.of("minecraft", "textures/block/light_gray_concrete.png"),
          cube[0],
          cube[1],
          cube[2],
          cube[3],
          cube[4],
          cube[5],
          light);
      matrices.pop();
    }
    ItemStack display = belt;
    if (state != null && state.hyper()) {
      display = belt.copy();
      NbtComponent.set(
          DataComponentTypes.CUSTOM_DATA,
          display,
          nbt -> nbt.putString("SVFRAME_ITEM_MODEL", "fantasyhub:item/additions/hyper_zecter"));
    }
    matrices.push();
    body.rotate(matrices);
    matrices.translate(0, .665, -.155);
    matrices.scale(.58f, -.38f, .55f);
    MinecraftClient.getInstance()
        .getItemRenderer()
        .renderItem(
            display,
            ModelTransformationMode.FIXED,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            vertices,
            player.getWorld(),
            player.getId());
    matrices.pop();
  }

  private static void renderParts(
      String form,
      String bone,
      ModelPart animated,
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      int light) {
    if (forms == null)
      try (var in =
          KabutoEquipmentFeature.class.getResourceAsStream(
              "/assets/fantasyhub/authored/kabuto_forms.json")) {
        if (in == null) throw new IllegalStateException("Missing authored Kabuto form geometry");
        forms =
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("forms");
      } catch (IOException error) {
        throw new IllegalStateException(error);
      }
    var parts = forms.getAsJsonObject(form).getAsJsonArray(bone);
    if (parts == null) return;
    for (var raw : parts) {
      var part = raw.getAsJsonObject();

      matrices.push();
      animated.rotate(matrices);
      if (part.has("rotation")) {
        float[] pivot = xyz(part, "pivot"), rotation = xyz(part, "rotation");
        matrices.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));
        matrices.translate(-pivot[0] / 16, -pivot[1] / 16, -pivot[2] / 16);
      }
      Identifier material = Identifier.of(part.get("material").getAsString());
      Identifier texture =
          Identifier.of(material.getNamespace(), "textures/" + material.getPath() + ".png");
      int partLight =
          part.has("emissive") && part.get("emissive").getAsBoolean()
              ? LightmapTextureManager.MAX_LIGHT_COORDINATE
              : light;
      renderGeometry(
          part,
          matrices,
          vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)),
          partLight);
      matrices.pop();
    }
  }

  private static void renderGeometry(
      JsonObject part, MatrixStack matrices, VertexConsumer vertices, int light) {
    for (var v : GEOMETRY.computeIfAbsent(part, KabutoEquipmentFeature::compileGeometry))
      vertices
          .vertex(matrices.peek(), v.x, v.y, v.z)
          .color(0xffffffff)
          .texture(v.u, v.v)
          .overlay(OverlayTexture.DEFAULT_UV)
          .light(light)
          .normal(matrices.peek(), v.nx, v.ny, v.nz);
  }

  private static List<Vertex> compileGeometry(JsonObject part) {
    List<Vertex> compiled = new ArrayList<>();
    float[][] points;
    List<int[]> polygons = new ArrayList<>();
    if (part.has("vertices")) {
      var raw = part.getAsJsonArray("vertices");
      points = new float[raw.size()][3];
      for (int i = 0; i < raw.size(); i++)
        for (int j = 0; j < 3; j++) points[i][j] = raw.get(i).getAsJsonArray().get(j).getAsFloat();
      for (var rawFace : part.getAsJsonArray("faces")) {
        var face = rawFace.getAsJsonArray();
        int[] ids = new int[face.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = face.get(i).getAsInt();
        polygons.add(ids);
      }
    } else {
      float[] a = xyz(part, "from"), d = xyz(part, "size");
      float x = a[0], y = a[1], z = a[2], X = x + d[0], Y = y + d[1], Z = z + d[2];
      points =
          new float[][] {
            {x, y, z}, {X, y, z}, {X, Y, z}, {x, Y, z}, {x, y, Z}, {X, y, Z}, {X, Y, Z}, {x, Y, Z}
          };
      polygons =
          List.of(
              new int[] {1, 0, 3, 2},
              new int[] {4, 5, 6, 7},
              new int[] {5, 1, 2, 6},
              new int[] {0, 4, 7, 3},
              new int[] {3, 7, 6, 2},
              new int[] {0, 1, 5, 4});
    }
    float[] uv = {0, 0, 1, 1};
    if (part.has("uv")) {
      var r = part.getAsJsonArray("uv");
      for (int i = 0; i < 4; i++) uv[i] = r.get(i).getAsFloat() / 256f;
    }
    for (int[] polygon : polygons) {
      if (polygon.length < 3) continue;
      var a = points[polygon[0]];
      var b = points[polygon[1]];
      var c = points[polygon[2]];
      var normal =
          new org.joml.Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2])
              .cross(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
      if (normal.lengthSquared() < .000001) continue;
      normal.normalize();
      int
          ax =
              Math.abs(normal.x) > Math.abs(normal.y) && Math.abs(normal.x) > Math.abs(normal.z)
                  ? 2
                  : 0,
          ay = Math.abs(normal.y) > Math.abs(normal.z) ? 2 : 1;
      float loX = Float.POSITIVE_INFINITY,
          hiX = Float.NEGATIVE_INFINITY,
          loY = Float.POSITIVE_INFINITY,
          hiY = Float.NEGATIVE_INFINITY;
      for (int id : polygon) {
        loX = Math.min(loX, points[id][ax]);
        hiX = Math.max(hiX, points[id][ax]);
        loY = Math.min(loY, points[id][ay]);
        hiY = Math.max(hiY, points[id][ay]);
      }
      for (int t = 1; t < polygon.length - 1; t++)
        for (int id : new int[] {polygon[0], polygon[t], polygon[t + 1], polygon[t + 1]}) {
          var p = points[id];
          float u = uv[0] + (uv[2] - uv[0]) * (p[ax] - loX) / Math.max(.001f, hiX - loX),
              v = uv[1] + (uv[3] - uv[1]) * (p[ay] - loY) / Math.max(.001f, hiY - loY);
          compiled.add(
              new Vertex(p[0] / 16, p[1] / 16, p[2] / 16, u, v, normal.x, normal.y, normal.z));
        }
    }
    return List.copyOf(compiled);
  }

  private static float[] xyz(JsonObject object, String key) {
    var array = object.getAsJsonArray(key);
    return new float[] {
      array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()
    };
  }

  private static void cube(
      MatrixStack matrices,
      VertexConsumerProvider vertices,
      Identifier texture,
      float x,
      float y,
      float z,
      float w,
      float h,
      float d,
      int light) {
    var cube =
        new ModelPart.Cuboid(
            0, 0, x, y, z, w, h, d, 0, 0, 0, false, 16, 16, EnumSet.allOf(Direction.class));
    cube.renderCuboid(
        matrices.peek(),
        vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(texture)),
        light,
        OverlayTexture.DEFAULT_UV,
        0xffffffff);
  }
}
