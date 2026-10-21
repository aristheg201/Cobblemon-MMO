package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProgressionPolicyTest {
  @Test void starterIsAvailableOnlyBeforeARealSelection() {
    assertTrue(ProgressionPolicy.starter(false, false));
    assertFalse(ProgressionPolicy.starter(true, false));
    assertFalse(ProgressionPolicy.starter(false, true));
  }
  @Test void defaultUnlockNeedsSixtySigilsAndAllowsClassOverrides() {
    var policy = ProgressionPolicy.defaults();
    assertEquals(60, policy.unlockCost("FLASH"));
    assertFalse(ProgressionPolicy.affordable(59, policy.unlockCost("FLASH")));
    assertTrue(ProgressionPolicy.affordable(60, policy.unlockCost("FLASH")));
    assertFalse(ProgressionPolicy.affordable(-1, 60));
    var custom = new ProgressionPolicy.Settings(60, Map.of("thor", 90), 1, 1, 1, false, false);
    assertEquals(90, custom.unlockCost("THOR"));
    assertEquals(60, custom.unlockCost("FLASH"));
    assertFalse(custom.switchFeesEnabled());
    assertFalse(custom.switchCooldownEnabled());
  }
  @Test void learningRequiresLevelFullPointCostAndNotAlreadyLearned() {
    assertFalse(ProgressionPolicy.learnable(4, 5, 1, false));
    assertTrue(ProgressionPolicy.learnable(5, 5, 1, false));
    assertFalse(ProgressionPolicy.learnable(5, 5, 0, false));
    assertFalse(ProgressionPolicy.learnable(5, 5, 1, true));
    assertFalse(ProgressionPolicy.learnable(5, 5, 1, false, 2));
    assertTrue(ProgressionPolicy.learnable(5, 5, 2, false, 2));
  }
  @Test void eighthAxeSkillHasItsOwnUnlockLevel() {
    assertEquals(12, ProgressionPolicy.requiredLevel(8));
    assertEquals(30, ProgressionPolicy.requiredLevel(7));
    assertEquals(1, ProgressionPolicy.requiredLevel(1));
  }
  @Test void migrationRespectsPreviouslySpentPointsAndGivesFirstLearningPoint() {
    var policy = ProgressionPolicy.defaults();
    assertEquals(1, ProgressionPolicy.migrationFloor(policy, 1, 0, 0));
    assertEquals(0, ProgressionPolicy.migrationFloor(policy, 1, 1, 0));
    assertEquals(7, ProgressionPolicy.migrationFloor(policy, 12, 3, 2));
    assertEquals(0, ProgressionPolicy.migrationFloor(policy, 5, 7, 0));
  }
  @Test void policyJsonRoundTripAndInvalidNegativeCostsFailClosed() {
    var gson = new Gson();
    assertEquals(ProgressionPolicy.defaults(), gson.fromJson(gson.toJson(ProgressionPolicy.defaults()), ProgressionPolicy.Settings.class));
    assertThrows(RuntimeException.class, () -> gson.fromJson("{\"defaultUnlockCost\":-1,\"learnCost\":1}", ProgressionPolicy.Settings.class));
    assertThrows(IllegalArgumentException.class, () -> new ProgressionPolicy.Settings(60, Map.of("THOR", -1), 1, 1, 1, true, true));
  }
}
