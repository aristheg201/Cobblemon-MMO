package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** GeckoLib and AzureLib scan every namespace's geo/ directory during startup. */
class NativeGeometryIsolationTest {
  private static final List<String> MODELS=List.of("cape_default","cape_batman","tao_mandala","mjolnir","stormbreaker");
  @Test void nativeFormatsAreNotExposedToBedrockGeometryLoaders() throws Exception {
    for(String name:MODELS) {
      assertNull(getClass().getResource("/assets/fantasyhub/geo/heroes/"+name+".json"),
          "Custom native model must not enter GeckoLib/AzureLib's reserved geo/ scan: "+name);
      try(var in=getClass().getResourceAsStream("/assets/fantasyhub/native_geometry/heroes/"+name+".json")) {
        assertNotNull(in,"Native renderer still needs the original geometry: "+name);
        assertTrue(JsonParser.parseReader(new InputStreamReader(in)).isJsonObject());
      }
    }
  }
  @Test void provenancePointsToPackagedNativeGeometry() throws Exception {
    try(var in=getClass().getResourceAsStream("/assets/fantasyhub/heroes-assets.json")) {
      assertNotNull(in);var files=JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject().getAsJsonArray("files");
      for(String name:MODELS) {
        String path="assets/fantasyhub/native_geometry/heroes/"+name+".json";
        assertTrue(java.util.stream.StreamSupport.stream(files.spliterator(),false)
            .anyMatch(e->e.getAsJsonObject().get("target").getAsString().equals(path)),path);
      }
    }
  }
  @Test void weaponRendererReadsRelocatedGeometry() throws Exception {
    var load=HeroEquipmentModels.class.getDeclaredMethod("load",String.class);load.setAccessible(true);
    for(String name:List.of("mjolnir","stormbreaker"))assertNotNull(load.invoke(null,name));
  }
}
