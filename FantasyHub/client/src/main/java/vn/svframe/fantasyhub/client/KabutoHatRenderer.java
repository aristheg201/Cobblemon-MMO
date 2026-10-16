package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Preserve arbitrary authored Euler angles instead of rounding1.21.11 geometry. */
final class KabutoHatRenderer {
  private static JsonArray elements;

  static void render(
      MatrixStack matrices, VertexConsumerProvider provider, int light, int overlay) {
    if (elements == null)
      try (var in =
          KabutoHatRenderer.class.getResourceAsStream(
              "/assets/fantasyhub/authored/kabuto_hat.json")) {
        if (in == null) throw new IllegalStateException("Missing original Kabuto helmet geometry");
        elements =
            JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonArray("elements");
      } catch (IOException failure) {
        throw new IllegalStateException(failure);
      }
    VertexConsumer vertices =
        provider.getBuffer(
            RenderLayer.getEntityCutoutNoCull(
                Identifier.of("fantasyhub", "textures/additions/kabuto_hat.png")));
    for (var raw : elements) {
      JsonObject e = raw.getAsJsonObject();
      float[] a = xyz(e, "from"), b = xyz(e, "to"), pivot = xyz(e, "origin");
      matrices.push();
      if (e.has("rotation")) {
        float[] r = xyz(e, "rotation");
        matrices.translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(r[2]));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(r[1]));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(r[0]));
        matrices.translate(-pivot[0] / 16, -pivot[1] / 16, -pivot[2] / 16);
      }
      for (var entry : e.getAsJsonObject("faces").entrySet()) {
        JsonObject face = entry.getValue().getAsJsonObject();
        if (!face.has("texture") || face.get("texture").isJsonNull()) continue;
        var uv = face.getAsJsonArray("uv");
        float u0 = uv.get(0).getAsFloat() / 64,
            v0 = uv.get(1).getAsFloat() / 64,
            u1 = uv.get(2).getAsFloat() / 64,
            v1 = uv.get(3).getAsFloat() / 64;
        float x0 = a[0] / 16,
            y0 = a[1] / 16,
            z0 = a[2] / 16,
            x1 = b[0] / 16,
            y1 = b[1] / 16,
            z1 = b[2] / 16;
        float[][] p;
        float nx = 0, ny = 0, nz = 0;
        switch (entry.getKey()) {
          case "north" -> {
            nz = -1;
            p = new float[][] {{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
          }
          case "south" -> {
            nz = 1;
            p = new float[][] {{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
          }
          case "east" -> {
            nx = 1;
            p = new float[][] {{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
          }
          case "west" -> {
            nx = -1;
            p = new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
          }
          case "up" -> {
            ny = 1;
            p = new float[][] {{x1, y1, z1}, {x0, y1, z1}, {x0, y1, z0}, {x1, y1, z0}};
          }
          case "down" -> {
            ny = -1;
            p = new float[][] {{x1, y0, z0}, {x0, y0, z0}, {x0, y0, z1}, {x1, y0, z1}};
          }
          default -> throw new IllegalStateException("Unknown authored face " + entry.getKey());
        }
        float[][] tex = {{u1, v0}, {u0, v0}, {u0, v1}, {u1, v1}};
        int turns = face.has("rotation") ? face.get("rotation").getAsInt() / 90 : 0;
        for (int i = 0; i < 4; i++) {
          var t = tex[Math.floorMod(i + turns, 4)];
          vertices
              .vertex(matrices.peek(), p[i][0], p[i][1], p[i][2])
              .color(0xffffffff)
              .texture(t[0], t[1])
              .overlay(overlay)
              .light(light)
              .normal(matrices.peek(), nx, ny, nz);
        }
      }
      matrices.pop();
    }
  }

  private static float[] xyz(JsonObject o, String key) {
    var a = o.getAsJsonArray(key);
    return a == null
        ? new float[] {0, 0, 0}
        : new float[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
  }
}
