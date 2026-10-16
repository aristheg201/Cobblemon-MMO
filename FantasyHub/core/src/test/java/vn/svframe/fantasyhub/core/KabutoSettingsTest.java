package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class KabutoSettingsTest {
  private Map<String, Object> defaults() throws Exception {
    return new HashMap<>(
        vn.svframe.svframelib.config.YamlLite.map(
            vn.svframe.svframelib.config.YamlLite.parse(
                Path.of("src/main/resources/default/kabuto/FantasyHub/kabuto.yml"))));
  }

  @Test
  void productionDefaultsHaveBoundedQueriesAndFiveSecondClock() throws Exception {
    var c = KabutoSettings.validate(defaults());
    assertEquals(100, ((Number) c.get("clock-duration-ticks")).intValue());
  }

  @Test
  void invalidAdminDataCannotCreateUnboundedEntityQueriesOrNonFiniteModifiers() throws Exception {
    for (var pair :
        Map.of(
                "clock-radius",
                100000d,
                "clock-speed-multiplier",
                Double.NaN,
                "masked-health",
                Double.POSITIVE_INFINITY,
                "clock-duration-ticks",
                .5)
            .entrySet()) {
      var c = defaults();
      c.put(pair.getKey(), pair.getValue());
      assertThrows(IllegalArgumentException.class, () -> KabutoSettings.validate(c));
    }
  }

  @Test
  void oldConfigsEnableNewBeltPassivesAndExplicitDisableIsPreserved() throws Exception {
    var old=defaults();old.remove("zecter-night-vision");old.remove("zecter-regeneration");
    var migrated=KabutoSettings.validate(old);
    assertEquals(true,migrated.get("zecter-night-vision"));assertEquals(true,migrated.get("zecter-regeneration"));
    old.put("zecter-night-vision",false);
    assertEquals(false,KabutoSettings.validate(old).get("zecter-night-vision"));
  }

  @Test
  void misspelledBooleanDoesNotSilentlyDisableNightVision() throws Exception {
    var invalid=defaults();invalid.put("zecter-night-vision","flase");
    assertThrows(IllegalArgumentException.class,()->KabutoSettings.validate(invalid));
  }
}
