package vn.svframe.fantasyhub.core.rider;

import java.util.*;

/** Bounded configuration is validated completely before changing the active runtime. */
public final class RiderSettings {
  private RiderSettings() {}

  /** New nested options inherit defaults while every explicit administrator value wins. */
  public static Map<String, Object> withDefaults(Map<?, ?> defaults, Map<?, ?> configured) {
    var merged = new LinkedHashMap<String, Object>();
    defaults.forEach((key, value) -> merged.put(String.valueOf(key), value));
    configured.forEach(
        (key, value) -> {
          String name = String.valueOf(key);
          Object baseline = merged.get(name);
          merged.put(
              name,
              baseline instanceof Map<?, ?> base && value instanceof Map<?, ?> override
                  ? withDefaults(base, override)
                  : value);
        });
    return merged;
  }

  public static Map<String, Object> validate(Map<String, Object> input) {
    if (!(input.get("schema") instanceof Number schema) || schema.doubleValue() != 1)
      throw new IllegalArgumentException("Unsupported Rider settings schema");
    var result = new LinkedHashMap<String, Object>();
    result.put("schema", 1);
    for (String type : List.of("agito", "zolda", "ryuki", "knight", "decade")) {
      if (!(input.get(type) instanceof Map<?, ?> raw))
        throw new IllegalArgumentException("Missing " + type);
      var values = new LinkedHashMap<String, Object>();
      for (var entry : raw.entrySet()) {
        String key = String.valueOf(entry.getKey());
        if (key.equals("skills")) continue;
        if (key.equals("friendly-fire") || key.startsWith("cc-immunity-")) {
          if (!(entry.getValue() instanceof Boolean)) invalid(type + "." + key);
          values.put(key, entry.getValue());
          continue;
        }
        double value = number(entry.getValue(), type + "." + key), min = 0, max = 1000;
        if (key.contains("reduction")
            || key.contains("fraction")
            || key.equals("mark-damage-per-stack")) max = 1;
        if (key.endsWith("multiplier")) {
          min = 0;
          max = 4;
        }
        if (key.endsWith("radius")) {
          min = .1;
          max = 16;
        }
        if (key.endsWith("ticks")) {
          min = 1;
          max = 1200;
          if (value != Math.rint(value)) invalid(type + "." + key);
        }
        if (key.equals("dimension-charge-ticks")) {
          min = 14;
          max = 600;
        }
        if (key.equals("final-projectiles")) {
          min = 1;
          max = 30;
          if (value != Math.rint(value)) invalid(key);
        }
        if (key.equals("final-range")) {
          min = 1;
          max = 64;
        }
        if (key.endsWith("range")) {
          min = 1;
          max = 64;
        }
        if (key.equals("collision-tolerance")) max = .5;
        if (key.endsWith("limit")
            || key.endsWith("projectiles")
            || key.equals("flame-stack-threshold")
            || key.equals("dimension-cards")) {
          min = 1;
          max = 32;
          if (value != Math.rint(value)) invalid(type + "." + key);
        }
        if (key.equals("dimension-cards")) {
          min = 5;
          max = 7;
        }
        if (key.equals("summon-limit")) max = 1;
        if (key.endsWith("chance")) max = 100;
        if (value < min || value > max) invalid(type + "." + key);
        values.put(key, entry.getValue());
      }
      if (!(raw.get("skills") instanceof Map<?, ?> skills))
        throw new IllegalArgumentException("Missing " + type + ".skills");
      var costs = new LinkedHashMap<String, Object>();
      for (var skill : skills.entrySet()) {
        if (!(skill.getValue() instanceof Map<?, ?> fields))
          throw new IllegalArgumentException("Invalid skill costs");
        var checked = new LinkedHashMap<String, Object>();
        for (String key : List.of("cooldown", "mana", "stamina")) {
          double value = number(fields.get(key), type + ".skills." + skill.getKey() + "." + key);
          if (value < 0 || value > (key.equals("cooldown") ? 600 : 1000)) invalid(key);
          checked.put(key, value);
        }
        costs.put(String.valueOf(skill.getKey()), Map.copyOf(checked));
      }
      values.put("skills", Map.copyOf(costs));
      result.put(type, Map.copyOf(values));
    }
    return Map.copyOf(result);
  }

  private static double number(Object raw, String name) {
    if (!(raw instanceof Number value) || !Double.isFinite(value.doubleValue()))
      throw new IllegalArgumentException("Invalid Rider setting: " + name);
    return value.doubleValue();
  }

  private static void invalid(String key) {
    throw new IllegalArgumentException("Rider setting out of range: " + key);
  }
}
