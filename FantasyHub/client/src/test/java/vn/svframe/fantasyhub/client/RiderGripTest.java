package vn.svframe.fantasyhub.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Checks the actual authored handle against the actual glove through vanilla held-item transforms.
 */
class RiderGripTest {
  @Test
  void bothHandsGraspEveryFormWeaponInsteadOfFloatingBehindTheHandle() throws Exception {
    JsonObject forms;
    try (var in = getClass().getResourceAsStream("/assets/fantasyhub/authored/rider_forms.json")) {
      forms =
          JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in)))
              .getAsJsonObject()
              .getAsJsonObject("forms");
    }
    for (var entry :
        Map.of(
                "shining_caliber",
                "agito_shining",
                "flame_saber",
                "agito_flame",
                "storm_halberd",
                "agito_storm",
                "drag_saber",
                "ryuki",
                "dark_visor",
                "knight",
                "dark_blade",
                "knight",
                "ride_booker_sword",
                "decade",
                "ride_booker_gun",
                "decade")
            .entrySet()) {
      var source =
          JsonParser.parseString(
                  Files.readString(Path.of("../assets-source/riders", entry.getKey() + ".bbmodel")))
              .getAsJsonObject();
      JsonObject grip = null;
      for (var raw : source.getAsJsonArray("elements"))
        if (Set.of(
                "shining_grip",
                "red_grip",
                "storm_hand_grip",
                "drag_grip",
                "dark_grip",
                "booker_grip")
            .contains(raw.getAsJsonObject().get("name").getAsString()))
          grip = raw.getAsJsonObject();
      assertNotNull(grip);
      Vector3f center =
          vector(grip.getAsJsonArray("from")).add(vector(grip.getAsJsonArray("to"))).mul(1f / 32);
      JsonObject model;
      try (var in =
          getClass()
              .getResourceAsStream(
                  "/assets/fantasyhub/models/item/riders/" + entry.getKey() + ".json")) {
        model =
            JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in)))
                .getAsJsonObject();
      }
      for (boolean left : new boolean[] {false, true}) {
        var transform =
            model
                .getAsJsonObject("display")
                .getAsJsonObject("thirdperson_" + (left ? "lefthand" : "righthand"));
        var rotation = vector(transform.getAsJsonArray("rotation")).mul((float) Math.PI / 180);
        if (left) {
          rotation.y = -rotation.y;
          rotation.z = -rotation.z;
        }
        var translation = vector(transform.getAsJsonArray("translation")).mul(1f / 16);
        if (left) translation.x = -translation.x;
        var held =
            new Matrix4f()
                .rotateX((float) -Math.PI / 2)
                .rotateY((float) Math.PI)
                .translate((left ? -1 : 1) / 16f, .125f, -.625f)
                .translate(translation)
                .rotateXYZ(rotation.x, rotation.y, rotation.z)
                .scale(vector(transform.getAsJsonArray("scale")))
                .translate(-.5f, -.5f, -.5f)
                .transformPosition(new Vector3f(center));
        JsonObject glove = null;
        String side = left ? "left" : "right";
        for (var raw : forms.getAsJsonObject(entry.getValue()).getAsJsonArray(side + "_arm"))
          if (raw.getAsJsonObject().get("name").getAsString().equals(side + "_glove"))
            glove = raw.getAsJsonObject();
        assertNotNull(glove);
        var low = vector(glove.getAsJsonArray("from")).mul(1f / 16);
        var high = new Vector3f(low).add(vector(glove.getAsJsonArray("size")).mul(1f / 16));
        assertTrue(
            held.x >= low.x
                && held.x <= high.x
                && held.y >= low.y
                && held.y <= high.y
                && held.z >= low.z
                && held.z <= high.z,
            entry.getKey()
                + " "
                + side
                + " grip "
                + held
                + " lies outside glove "
                + low
                + ".."
                + high);
      }
    }
  }

  private static Vector3f vector(JsonArray a) {
    return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
  }
}
