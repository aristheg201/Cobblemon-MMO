package vn.svframe.svframelib.skill;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class SkillCastPoliciesTest {
  @Test void controlGuardBlocksAllActivesWithoutChangingOtherPlayers() throws Exception {
    var blocked=UUID.randomUUID();var free=UUID.randomUUID();
    try(var policy=SkillCastPolicies.register("hero-test",(p,s)->!p.equals(blocked),(p,s)->.5)) {
      assertFalse(SkillCastPolicies.allows(blocked,"RYUKI_FINAL_VENT"));
      assertFalse(SkillCastPolicies.allows(blocked,"IRON_MAN_REPULSOR_BLAST"));
      assertTrue(SkillCastPolicies.allows(free,"IRON_MAN_REPULSOR_BLAST"));
      assertEquals(.5,SkillCastPolicies.cooldownReduction(free,"IRON_MAN_REPULSOR_BLAST",.9));
    }
    assertTrue(SkillCastPolicies.allows(blocked,"RYUKI_FINAL_VENT"));
    assertEquals(.9,SkillCastPolicies.cooldownReduction(free,"IRON_MAN_REPULSOR_BLAST",.9));
  }
  @Test void closingOldRegistrationCannotEraseItsReplacement() throws Exception {
    var id=UUID.randomUUID();var old=SkillCastPolicies.register("hero-replace",(p,s)->true,(p,s)->1);
    try(var replacement=SkillCastPolicies.register("hero-replace",(p,s)->false,(p,s)->0)) {
      old.close();assertFalse(SkillCastPolicies.allows(id,"ANY"));
    }
    assertTrue(SkillCastPolicies.allows(id,"ANY"));
  }
  @Test void capCoversSpecificSkillAndGlobalCooldownReductionsTogether() throws Exception {
    var id=UUID.randomUUID();
    try(var policy=SkillCastPolicies.register("combined-cap",(p,s)->true,(p,s)->.5)) {
      assertEquals(10,SkillCastPolicies.cooldownSeconds(id,"THOR_MJOLNIR_THROW",20,12,.5));
      assertEquals(12,SkillCastPolicies.cooldownSeconds(id,"THOR_MJOLNIR_THROW",20,12,0));
    }
    assertEquals(6,SkillCastPolicies.cooldownSeconds(id,"OTHER",20,12,.5));
  }
  @Test void nonfiniteOrNegativeReductionNeverCreatesInfiniteCooldownBypass() {
    var id=UUID.randomUUID();assertEquals(0,SkillCastPolicies.cooldownReduction(id,"ANY",Double.NaN));
    assertEquals(0,SkillCastPolicies.cooldownReduction(id,"ANY",-.2));
    assertEquals(1,SkillCastPolicies.cooldownReduction(id,"ANY",9));
  }
}
