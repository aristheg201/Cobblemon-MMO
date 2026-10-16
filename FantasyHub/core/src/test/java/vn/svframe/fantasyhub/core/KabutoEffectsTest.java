package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class KabutoEffectsTest {
  private Map<String, Object> defaults() {
    return new HashMap<>(
        Map.of(
            "enabled",
            true,
            "minecraft",
            true,
            "cobblemon",
            true,
            "view-distance",
            48,
            "density",
            1d,
            "max-events-per-tick",
            96));
  }

  @Test
  void invalidAdminInputCannotExpandNetworkFanoutOrProduceNonFiniteGeometry() {
    for (var pair :
        Map.<String, Object>of(
                "view-distance",
                100000,
                "density",
                Double.NaN,
                "max-events-per-tick",
                100000,
                "cobblemon",
                "true")
            .entrySet()) {
      var config = defaults();
      config.put(pair.getKey(), pair.getValue());
      assertThrows(IllegalArgumentException.class, () -> KabutoEffects.validate(config));
    }
    var config = defaults();
    config.put("max-events-per-tick", 1.5);
    assertThrows(IllegalArgumentException.class, () -> KabutoEffects.validate(config));
  }

  @Test
  void DisablingPresentationPreservesConfigWithoutRequiringACombatChange() {
    var config = defaults();
    config.put("enabled", false);
    assertEquals(false, KabutoEffects.validate(config).get("enabled"));
  }
}
