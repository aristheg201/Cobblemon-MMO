package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Cached bone-local Blockbench geometry. All shading uses the authored 512px atlas. */
final class RiderModels {
  static final Set<String> FORMS =
      Set.of(
          "zolda",
          "agito_ground",
          "agito_storm",
          "agito_flame",
          "agito_trinity",
          "agito_shining",
          "magnugiga",
          "ryuki",
          "knight",
          "decade",
          "dragreder",
          "dragranzer",
          "darkwing",
          "rider_card",
          "drag_shield",
          "drag_visor",
          "drag_claw",
          "drag_visor_z",
          "ktouch",
          "knight_cloak",
          "kuuga_ultimate",
          "blade_king",
          "kiva_emperor");
  static final Set<String> WEARABLE =
      Set.of(
          "zolda",
          "agito_ground",
          "agito_storm",
          "agito_flame",
          "agito_trinity",
          "agito_shining",
          "ryuki",
          "knight",
          "decade");
  private static final Identifier TEXTURE =
      Identifier.of("fantasyhub", "textures/author/rider_metals.png");

  private record Vertex(
      float x, float y, float z, float u, float v, float nx, float ny, float nz) {}

  private static final Map<JsonObject, List<Vertex>> GEOMETRY = new IdentityHashMap<>();
  private static JsonObject forms;

  private static JsonObject forms() {
    if (forms != null) return forms;
    try (var stream =
        RiderModels.class.getResourceAsStream("/assets/fantasyhub/authored/rider_forms.json")) {
      if (stream == null) throw new IllegalStateException("Missing Rider geometry");
      var root =
          JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
              .getAsJsonObject();
      if (root.get("texture_size").getAsInt() != 512)
        throw new IllegalStateException("Rider atlas dimensions mismatch");
      return forms = root.getAsJsonObject("forms");
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot load Rider geometry", failure);
    }
  }

  static void render(
      String form,
      String bone,
      ModelPart animated,
      MatrixStack matrices,
      VertexConsumerProvider provider,
      int light,
      boolean expanded,
      java.util.function.Predicate<JsonObject> filter) {
    renderTint(form, bone, animated, matrices, provider, light, expanded, filter, 0xffffffff);
  }

  private static void renderTint(
      String form,
      String bone,
      ModelPart animated,
      MatrixStack matrices,
      VertexConsumerProvider provider,
      int light,
      boolean expanded,
      java.util.function.Predicate<JsonObject> filter,
      int tint) {
    if (!FORMS.contains(form)) return;
    var parts = forms().getAsJsonObject(form).getAsJsonArray(bone);
    var consumer =
        provider.getBuffer(
            (tint >>> 24) < 255
                ? RenderLayer.getEntityTranslucent(TEXTURE)
                : RenderLayer.getEntityCutoutNoCull(TEXTURE));
    for (var raw : parts) {
      var part = raw.getAsJsonObject();
      if (part.has("requires_force") && part.get("requires_force").getAsBoolean() && !expanded
          || !filter.test(part)) continue;
      matrices.push();
      if (animated != null) animated.rotate(matrices);
      if (part.has("animation")) {
        var a = part.getAsJsonObject("animation");
        var p = a.getAsJsonArray("pivot");
        double x = p.get(0).getAsDouble() / 16,
            y = p.get(1).getAsDouble() / 16,
            z = p.get(2).getAsDouble() / 16;
        var client = net.minecraft.client.MinecraftClient.getInstance();
        double time =
            client.world == null
                ? 0
                : (client.world.getTime() + client.getRenderTickCounter().getTickDelta(false))
                    / 20d;
        float angle =
            (float)
                (Math.sin(
                        (time / a.get("period").getAsDouble() + a.get("phase").getAsDouble())
                            * Math.PI
                            * 2)
                    * a.get("amplitude").getAsDouble());
        matrices.translate(x, y, z);
        matrices.multiply(
            (a.get("axis").getAsString().equals("y")
                    ? RotationAxis.POSITIVE_Y
                    : RotationAxis.POSITIVE_Z)
                .rotationDegrees(angle));
        matrices.translate(-x, -y, -z);
      }
      if (part.has("rotation")) {
        var pivot = xyz(part, "pivot");
        var rotation = xyz(part, "rotation");
        matrices.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));
        matrices.translate(-pivot[0] / 16, -pivot[1] / 16, -pivot[2] / 16);
      }
      for (var v : GEOMETRY.computeIfAbsent(part, RiderModels::compileGeometry))
        consumer
            .vertex(matrices.peek(), v.x, v.y, v.z)
            .color(tint)
            .texture(v.u, v.v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(matrices.peek(), v.nx, v.ny, v.nz);
      matrices.pop();
    }
  }

  static void display(
      String form,
      MatrixStack matrices,
      VertexConsumerProvider provider,
      int light,
      boolean expanded) {
    displayTint(form, matrices, provider, light, expanded, 0xffffffff);
  }

  static void displayTint(
      String form,
      MatrixStack matrices,
      VertexConsumerProvider provider,
      int light,
      boolean expanded,
      int tint) {
    displayPose(form, matrices, provider, light, expanded, tint, 0);
  }

  static void displayPose(
      String form,
      MatrixStack matrices,
      VertexConsumerProvider provider,
      int light,
      boolean expanded,
      int tint,
      float attack) {
    for (String bone : List.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg")) {
      matrices.push();
      switch (bone) {
        case "head", "body" -> matrices.translate(0, 1.5, 0);
        case "left_arm" -> matrices.translate(.3125, 1.375, 0);
        case "right_arm" -> matrices.translate(-.3125, 1.375, 0);
        case "left_leg" -> matrices.translate(.125, .75, 0);
        case "right_leg" -> matrices.translate(-.125, .75, 0);
      }
      if (attack > 0) {
        if (bone.equals("right_arm"))
          matrices.multiply(
              RotationAxis.POSITIVE_X.rotationDegrees(
                  (form.equals("kiva_emperor") ? -30 : 90) * attack));
        if (bone.equals("left_arm"))
          matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(35 * attack));
        if (bone.equals("right_leg") && form.equals("kiva_emperor"))
          matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(80 * attack));
        if (bone.equals("body"))
          matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-12 * attack));
      }
      matrices.scale(1, -1, -1);
      renderTint(form, bone, null, matrices, provider, light, expanded, p -> true, tint);
      if ((tint >>> 24) < 255
          && bone.equals("right_arm")
          && (form.equals("knight") || form.equals("decade"))) {
        // Same arm-local transform as the real player's held-item feature and grip tests.
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        matrices.translate(1 / 16d, .125, -.625);
        var client = net.minecraft.client.MinecraftClient.getInstance();
        client
            .getItemRenderer()
            .renderItem(
                RiderWeapons.modelStack(form.equals("knight") ? "dark_visor" : "ride_booker_sword"),
                net.minecraft.client.render.model.json.ModelTransformationMode
                    .THIRD_PERSON_RIGHT_HAND,
                light,
                OverlayTexture.DEFAULT_UV,
                matrices,
                provider,
                client.world,
                0);
      }
      matrices.pop();
    }
  }

  static void object(
      String name, MatrixStack matrices, VertexConsumerProvider provider, int light, int tint) {
    matrices.push();
    matrices.scale(1, -1, -1);
    localObject(name, matrices, provider, light, tint);
    matrices.pop();
  }

  static void localObject(
      String name, MatrixStack matrices, VertexConsumerProvider provider, int light, int tint) {
    renderTint(name, "body", null, matrices, provider, light, false, p -> true, tint);
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
      for (int i = 0; i < 4; i++) uv[i] = r.get(i).getAsFloat() / 512f;
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
}
