package vn.svframe.fantasyhub.core;

import java.util.*;

/** Bound administrator data before any spatial query, modifier or scheduler uses it. */
final class KabutoSettings {
  static Map<String, Object> validate(Map<String, Object> source) {
    for (String key :
        List.of(
            "energy-per-hit",
            "energy-per-block",
            "gauge-per-hit",
            "gauge-per-block",
            "masked-health",
            "masked-armor",
            "masked-parry-chance",
            "masked-knockback-resistance",
            "rider-critical-chance",
            "rider-dodge-chance",
            "cast-off-damage",
            "slam-damage",
            "kick-damage",
            "kunai-damage",
            "gun-damage",
            "bleed-damage",
            "kick-combo-damage-per-point",
            "empowered-damage-multiplier")) range(source, key, 0, 10000);
    for (String key :
        List.of(
            "clock-duration-ticks",
            "bleed-duration-ticks",
            "clock-gauge-cost",
            "energy-per-hit",
            "energy-per-block",
            "gauge-per-hit",
            "gauge-per-block")) {
      if (!(source.get(key) instanceof Number number))
        throw new IllegalArgumentException("Missing Kabuto integer: " + key);
      double value = number.doubleValue();
      if (value != Math.rint(value))
        throw new IllegalArgumentException("Kabuto " + key + " must be an integer");
    }
    for (var row : source.entrySet())
      if (row.getValue() instanceof Number n
          && (!Double.isFinite(n.doubleValue()) || n.doubleValue() < 0 || n.doubleValue() > 10000))
        throw new IllegalArgumentException("Invalid Kabuto value: " + row.getKey());
    range(source, "clock-duration-ticks", 1, 1200);
    range(source, "clock-gauge-cost", 1, 100);
    range(source, "clock-radius", 1, 32);
    range(source, "clock-speed-multiplier", 1, 5);
    range(source, "masked-speed-multiplier", .1, 1);
    range(source, "rider-attack-speed-multiplier", 1, 5);
    range(source, "empowered-cooldown-reduction", 0, 1);
    range(source, "rider-dodge-chance", 0, 100);
    range(source, "rider-critical-chance", 0, 100);
    range(source, "bleed-duration-ticks", 1, 600);
    if (!(source.get("skills") instanceof Map<?, ?> skills))
      throw new IllegalArgumentException("Missing Kabuto skill costs");
    for (Object raw : skills.values()) {
      if (!(raw instanceof Map<?, ?> costs))
        throw new IllegalArgumentException("Invalid Kabuto skill costs");
      for (Object value : costs.values())
        if (!(value instanceof Number n)
            || !Double.isFinite(n.doubleValue())
            || n.doubleValue() < 0
            || n.doubleValue() > 10000)
          throw new IllegalArgumentException("Invalid Kabuto skill cost");
    }
    return Map.copyOf(source);
  }

  private static void range(Map<String, Object> source, String key, double min, double max) {
    if (!(source.get(key) instanceof Number n)
        || n.doubleValue() < min
        || n.doubleValue() > max
        || !Double.isFinite(n.doubleValue()))
      throw new IllegalArgumentException("Kabuto " + key + " must be from " + min + " to " + max);
  }
}
