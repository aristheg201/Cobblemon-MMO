package vn.svframe.fantasyhub.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Source identity and native model-format checks, separate from visual runtime acceptance. */
class RiderAssetContractTest {
  private JsonObject asset(String path) throws Exception {
    try (var in = getClass().getResourceAsStream("/assets/fantasyhub/" + path)) {
      assertNotNull(in, path);
      return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
          .getAsJsonObject();
    }
  }

  private JsonObject part(JsonObject form, String bone, String name) {
    for (var raw : form.getAsJsonArray(bone))
      if (raw.getAsJsonObject().get("name").getAsString().equals(name))
        return raw.getAsJsonObject();
    throw new AssertionError("Missing " + bone + "/" + name);
  }

  @Test
  void elementalArmsStayAsymmetricAndRearEquipmentExistsOnEveryForm() throws Exception {
    var forms = asset("authored/rider_forms.json").getAsJsonObject("forms");
    assertEquals(
        "blue",
        materialTile(
            part(forms.getAsJsonObject("agito_storm"), "left_arm", "left_square_pauldron")));
    assertEquals(
        "carbon",
        materialTile(
            part(forms.getAsJsonObject("agito_storm"), "right_arm", "right_square_pauldron")));
    assertEquals(
        "red",
        materialTile(
            part(forms.getAsJsonObject("agito_flame"), "right_arm", "right_square_pauldron")));
    assertEquals(
        "carbon",
        materialTile(
            part(forms.getAsJsonObject("agito_flame"), "left_arm", "left_square_pauldron")));
    for (String name :
        List.of(
            "zolda",
            "agito_ground",
            "agito_storm",
            "agito_flame",
            "agito_trinity",
            "agito_shining")) {
      var form = forms.getAsJsonObject(name);
      assertNotNull(form, name);
      assertNotNull(part(form, "body", "waist_rear_clasp"));
      for (String side : List.of("left", "right")) {
        assertNotNull(part(form, side + "_arm", side + "_rear_bracer_panel"));
        assertNotNull(part(form, side + "_leg", side + "_boot_heel_cap"));
      }
    }
  }

  private String materialTile(JsonObject part) {
    int tile =
        part.getAsJsonArray("uv").get(0).getAsInt() / 64
            + part.getAsJsonArray("uv").get(1).getAsInt() / 64 * 8;
    return List.of(
            "gold",
            "gold_edge",
            "gold_dark",
            "steel",
            "chrome",
            "steel_dark",
            "green",
            "green_dark",
            "green_light",
            "red",
            "red_edge",
            "blue",
            "blue_edge",
            "carbon",
            "fabric",
            "black",
            "white",
            "lens_red",
            "lens_green",
            "lens_amber",
            "amber",
            "gem",
            "silver_white")
        .get(tile);
  }

  @Test
  void allNativeEquipmentFitsJavaElementBoundsAndAllowedRotations() throws Exception {
    for (String name :
        List.of(
            "zolda_belt",
            "agito_belt",
            "magna_visor",
            "giga_launcher",
            "flame_saber",
            "storm_halberd",
            "shining_caliber", "ryuki_belt", "knight_belt", "decade_belt", "drag_saber", "dark_visor", "dark_blade", "ride_booker_sword", "ride_booker_gun")) {
      var model = asset("models/item/riders/" + name + ".json");
      for (var raw : model.getAsJsonArray("elements")) {
        var cube = raw.getAsJsonObject();
        for (String end : List.of("from", "to"))
          for (var n : cube.getAsJsonArray(end))
            assertTrue(n.getAsDouble() >= -16 && n.getAsDouble() <= 32, name + " " + cube);
        for (int axis = 0; axis < 3; axis++)
          assertTrue(
              cube.getAsJsonArray("from").get(axis).getAsDouble()
                  < cube.getAsJsonArray("to").get(axis).getAsDouble());
        if (cube.has("rotation"))
          assertTrue(
              Set.of(-45d, -22.5d, 0d, 22.5d, 45d)
                  .contains(cube.getAsJsonObject("rotation").get("angle").getAsDouble()));
      }
    }
  }

  @Test
  void templeAndMaskMeshesKeepTheirFrontAndBackFacesAfterExport() throws Exception {
    var forms = asset("authored/rider_forms.json").getAsJsonObject("forms");
    for (var form : forms.entrySet())
      for (var raw : form.getValue().getAsJsonObject().getAsJsonArray("head")) {
        var part = raw.getAsJsonObject();
        if (!part.has("vertices")) continue;
        Map<String, Integer> edges = new HashMap<>();
        for (var face : part.getAsJsonArray("faces")) {
          var indices = face.getAsJsonArray();
          for (int i = 0; i < indices.size(); i++) {
            int a = indices.get(i).getAsInt(), b = indices.get((i + 1) % indices.size()).getAsInt();
            edges.merge(Math.min(a, b) + ":" + Math.max(a, b), 1, Integer::sum);
          }
        }
        for (var edge : edges.entrySet())
          assertEquals(
              2,
              edge.getValue(),
              form.getKey() + "/" + part.get("name") + " open edge " + edge.getKey());
      }
  }
}
