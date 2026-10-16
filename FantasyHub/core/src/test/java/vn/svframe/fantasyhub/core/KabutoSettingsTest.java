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
}
