package vn.svframe.fantasyhub.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Asset identity contracts; actual resemblance is reviewed in native captures separately. */
class ArmorReferenceRevisionTest {
  @Test void everyExportedRiderModelIsPreloadedIncludingSummonActors() throws Exception {
    var manifest=getClass().getResource("/assets/fantasyhub/riders-models.json");assertNotNull(manifest);
    Path path=Path.of(manifest.toURI());var listed=new java.util.HashSet<String>();
    JsonParser.parseString(Files.readString(path)).getAsJsonArray().forEach(e->listed.add(e.getAsString()));
    Path modelRoot=path.getParent().resolve("models");
    try(var files=Files.walk(modelRoot.resolve("item/riders"))) {
      for(Path f:files.filter(f->f.toString().endsWith(".json")).toList())
        assertTrue(listed.contains("fantasyhub:"+modelRoot.relativize(f).toString().replace('\\','/').replaceAll("\\.json$","")),f.toString());
    }
    for(String actor:new String[]{"magnugiga","dragreder","dragranzer","darkwing"})assertTrue(listed.contains("fantasyhub:item/riders/"+actor));
  }
  private JsonObject forms() throws Exception {
    try (var in=getClass().getResourceAsStream("/assets/fantasyhub/authored/rider_forms.json")) {
      assertNotNull(in);
      return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("forms");
    }
  }
  private JsonObject part(JsonObject form,String bone,String name) {
    for(var p:form.getAsJsonArray(bone))if(p.getAsJsonObject().get("name").getAsString().equals(name))return p.getAsJsonObject();
    throw new AssertionError(name);
  }
  private int tile(JsonObject p) {
    var uv=p.getAsJsonArray("uv");return uv.get(0).getAsInt()/64+8*(uv.get(1).getAsInt()/64);
  }
  @Test void normalAndSurviveHaveDistinctGeometryAndCorrectDecks() throws Exception {
    var fs=forms();var normal=fs.getAsJsonObject("ryuki");var survive=fs.getAsJsonObject("ryuki_survive");
    assertNotEquals(normal,survive);
    assertTrue(RiderModels.WEARABLE.contains("ryuki_survive"));
    assertEquals(57,tile(part(normal,"body","belt_gold_dragon_crest_card_deck")));
    assertEquals(58,tile(part(survive,"body","belt_gold_dragon_crest_card_deck")));
    assertNotNull(part(survive,"body","survive_crimson_dragon_breast"));
    assertNotNull(part(survive,"head","survive_gold_swept_crown_-1"));
    assertNotNull(part(survive,"head","survive_swept_gold_temple_-1"));
    assertNotNull(part(normal,"head","normal_ryuki_forehead_crest"));
  }
  @Test void redLegPanelsAndSurviveGoldGreavesAreNotSharedOrRecoloredPlaceholders() throws Exception {
    var fs=forms();
    for(String side:new String[]{"left","right"}) {
      assertEquals(41,tile(part(fs.getAsJsonObject("ryuki"),side+"_leg",side+"_red_shin_front")));
      assertEquals(50,tile(part(fs.getAsJsonObject("ryuki_survive"),side+"_leg",side+"_survive_gold_shin")));
      assertNotNull(part(fs.getAsJsonObject("ryuki_survive"),side+"_arm",side+"_survive_gold_wing_edge"));
    }
  }
  @Test void decadeHasIntegralBarcodeSeparateGreenLensesAndDetailedDriver() throws Exception {
    var d=forms().getAsJsonObject("decade");
    for(int i=0;i<7;i++)assertNotNull(part(d,"head","integral_barcode_face_"+i));
    for(int sign:new int[]{-1,1})assertEquals(56,tile(part(d,"head","voxel_green_lens_"+sign)));
    assertEquals(59,tile(part(d,"body","belt_nine_rider_emblems_and_red_core")));
    assertNotNull(part(d,"body","white_diagonal_frame"));
    assertFalse(RiderModels.WEARABLE.contains("kuuga_ultimate"));
  }
}
