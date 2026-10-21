package vn.svframe.fantasyhub.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Pure, validated policy. Native exp-tables remain responsible for level-up skill points. */
public final class ProgressionPolicy {
  private ProgressionPolicy() {}

  public record Settings(int defaultUnlockCost, Map<String, Integer> classUnlockCosts,
                         int learnCost, int initialSkillPoints, int migrationPointsPerLevel,
                         boolean switchFeesEnabled, boolean switchCooldownEnabled) {
    public Settings {
      check(defaultUnlockCost, 0, 1_000_000, "defaultUnlockCost");
      check(learnCost, 1, 1000, "learnCost");
      check(initialSkillPoints, 0, 1000, "initialSkillPoints");
      check(migrationPointsPerLevel, 0, 1000, "migrationPointsPerLevel");
      var normalized = new LinkedHashMap<String, Integer>();
      if (classUnlockCosts != null) classUnlockCosts.forEach((id, cost) -> {
        if (id == null || !id.matches("[A-Za-z0-9_]+") || cost == null)
          throw new IllegalArgumentException("Invalid class unlock override");
        check(cost, 0, 1_000_000, "class unlock cost");
        if (normalized.put(id.toUpperCase(Locale.ROOT), cost) != null)
          throw new IllegalArgumentException("Duplicate class unlock override: " + id);
      });
      classUnlockCosts = Map.copyOf(normalized);
    }

    public int unlockCost(String id) { return classUnlockCosts.getOrDefault(id.toUpperCase(Locale.ROOT), defaultUnlockCost); }
  }

  public static Settings defaults() { return new Settings(60, Map.of(), 1, 1, 1, true, true); }
  public static boolean starter(boolean chosen, boolean consumed) { return !chosen && !consumed; }
  public static boolean affordable(int balance, int price) { return price >= 0 && balance >= price; }
  public static boolean learnable(int level, int required, int points, boolean learned) { return learnable(level, required, points, learned, 1); }
  public static boolean learnable(int level, int required, int points, boolean learned, int cost) {
    return cost > 0 && level >= required && points >= cost && !learned;
  }
  public static int requiredLevel(int index) { return index == 8 ? 12 : Math.max(1, (index - 1) * 5); }
  public static int migrationFloor(Settings policy, int level, int learnedSkills, int upgradedPoints) {
    long total = Math.max(policy.initialSkillPoints(), (long) Math.max(1, level) * policy.migrationPointsPerLevel());
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0, total - (long) learnedSkills * policy.learnCost() - upgradedPoints));
  }
  private static void check(int value, int min, int max, String field) {
    if (value < min || value > max) throw new IllegalArgumentException("Invalid " + field);
  }
}
