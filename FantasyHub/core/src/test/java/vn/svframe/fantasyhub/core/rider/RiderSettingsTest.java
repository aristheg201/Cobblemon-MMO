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
  void completeControlImmunityAndCardChargeHaveValidatedConfiguration() throws Exception {
    var config = new HashMap<>(defaults());
    var decade = new HashMap<>(YamlLite.map(config.get("decade")));
    decade.put("cc-immunity-blindness", false);
    config.put("decade", decade);
    var valid = RiderSettings.validate(config);
    assertEquals(false, ((Map<?, ?>) valid.get("decade")).get("cc-immunity-blindness"));
    decade.put("cc-immunity-blindness", "true");
    assertThrows(IllegalArgumentException.class, () -> RiderSettings.validate(config));
    decade.put("cc-immunity-blindness", true);
    decade.put("dimension-charge-ticks", 1);
    assertThrows(IllegalArgumentException.class, () -> RiderSettings.validate(config));
  }

  @Test
  void partialLegacySkillCostsInheritNewFieldsWithoutResettingCustomBalance() throws Exception {
    var bundled = defaults();
    var custom =
        Map.of("decade", Map.of("skills", Map.of("ULTIMATE_SUMMON", Map.of("cooldown", 123))));
    var merged = RiderSettings.validate(RiderSettings.withDefaults(bundled, custom));
    var decade = (Map<?, ?>) merged.get("decade");
    var skills = (Map<?, ?>) decade.get("skills");
    var ultimate = (Map<?, ?>) skills.get("ULTIMATE_SUMMON");
    assertEquals(123, ((Number) ultimate.get("cooldown")).intValue());
    assertEquals(16, ((Number) ultimate.get("mana")).intValue());
    assertTrue(skills.containsKey("DIMENSION_KICK"));
    assertEquals(true, decade.get("cc-immunity-stun"));
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
