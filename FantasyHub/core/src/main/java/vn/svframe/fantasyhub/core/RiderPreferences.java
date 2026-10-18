package vn.svframe.fantasyhub.core;

import java.util.Locale;
import vn.svframe.svframemmo.SVFrameMMO;

/** Uses the canonical MMO UUID profile and its debounced writer; no second Rider save format. */
final class RiderPreferences {
  private static final String PREFIX = "fantasyhub.rider.";

  static void restore(RiderRuntime.Session s) {
    var data = SVFrameMMO.playerData().get(s.player);
    s.cards.selectedSpirit = (int) number(data.getPreference(PREFIX + "spirit", "1"), 1, 1, 3);
    s.cards.ultimateProjection =
        (int) number(data.getPreference(PREFIX + "projection", "0"), 0, 0, 2);
    var cooldowns = data.getMMOPlayerData().getCooldownMap();
    long now = System.currentTimeMillis();
    for (String ability : RiderRuntime.SKILLS.get(s.type)) {
      String path = s.type.toLowerCase(Locale.ROOT) + "_" + ability.toLowerCase(Locale.ROOT);
      String key = PREFIX + "cooldown." + path;
      long expires = number(data.getPreference(key, "0"), 0, 0, Long.MAX_VALUE);
      double seconds = Math.min(600, Math.max(0, expires - now) / 1000d);
      var current = cooldowns.getInfo(path);
      if (seconds > 0 && (current == null || current.getNextUse() < expires))
        cooldowns.applyCooldown(path, seconds);
      else if (seconds <= 0) data.setPreference(key, null);
    }
    // Temporary forms intentionally reset to human on reconnect, death and dimension change.
    // Re-entering a stance requires a validated skill cast; no offline buff reconstruction.
  }

  static void accepted(RiderRuntime.Session s, String ability) {
    var data = SVFrameMMO.playerData().get(s.player);
    String path = s.type.toLowerCase(Locale.ROOT) + "_" + ability.toLowerCase(Locale.ROOT);
    var cooldown = data.getMMOPlayerData().getCooldownMap().getInfo(path);
    if (cooldown != null)
      data.setPreference(PREFIX + "cooldown." + path, Long.toString(cooldown.getNextUse()));
    data.setPreference(PREFIX + "class", s.type);
    data.setPreference(PREFIX + "spirit", Integer.toString(s.cards.selectedSpirit));
    data.setPreference(PREFIX + "projection", Integer.toString(s.cards.ultimateProjection));
    data.setPreference(PREFIX + "last-stance", s.cards.elevated ? "elevated" : "normal");
  }

  private static long number(String text, long fallback, long min, long max) {
    try {
      return Math.clamp(Long.parseLong(text), min, max);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }
}
