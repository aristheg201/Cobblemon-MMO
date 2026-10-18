package vn.svframe.fantasyhub.core.rider;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.config.YamlLite;

class RiderSettingsTest {
  private Map<String, Object> defaults() throws Exception {
    return YamlLite.map(
        YamlLite.parse(Path.of("src/main/resources/default/riders/FantasyHub/riders.yml")));
  }

  @Test
  void authoredTimersAndArtilleryBudgetAreBounded() throws Exception {
    var config = RiderSettings.validate(defaults());
    assertEquals(
        240, ((Number) ((Map<?, ?>) config.get("agito")).get("shining-duration-ticks")).intValue());
    assertEquals(
        24, ((Number) ((Map<?, ?>) config.get("zolda")).get("final-projectiles")).intValue());
    assertThrows(
        UnsupportedOperationException.class,
        () -> ((Map<String, Object>) config.get("zolda")).put("final-projectiles", 10000));
  }

  @Test
  void unboundedOrInvalidAdministratorSettingsAreRejectedBeforeActivation() throws Exception {
    for (var entry :
        Map.<String, Object>of(
                "final-projectiles",
                10000,
                "guard-reduction",
                1.2,
                "rocket-radius",
                Double.NaN,
                "mark-duration-ticks",
                1.5)
            .entrySet()) {
      var config = new HashMap<>(defaults());
      var zolda = new HashMap<>(YamlLite.map(config.get("zolda")));
      zolda.put(entry.getKey(), entry.getValue());
      config.put("zolda", zolda);
      assertThrows(
          IllegalArgumentException.class, () -> RiderSettings.validate(config), entry.getKey());
    }
  }

  @Test
  void negativeAndInfiniteCostsCannotGenerateFreeResources() throws Exception {
    for (double value : new double[] {-1, Double.POSITIVE_INFINITY}) {
      var config = new HashMap<>(defaults());
      var zolda = new HashMap<>(YamlLite.map(config.get("zolda")));
      var skills = new HashMap<>(YamlLite.map(zolda.get("skills")));
      var costs = new HashMap<>(YamlLite.map(skills.get("FINAL_VENT")));
      costs.put("mana", value);
      skills.put("FINAL_VENT", costs);
      zolda.put("skills", skills);
      config.put("zolda", zolda);
      assertThrows(IllegalArgumentException.class, () -> RiderSettings.validate(config));
    }
  }
}
