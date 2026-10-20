package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class HeroPowerStateTest {
  @Test void temporaryStacksExpireIndependentlyAndNeverExceedTheirCap() {
    var s=new HeroPowerState();s.gain("instinct",5,120,0);s.gain("instinct",5,120,40);
    assertEquals(2,s.stacks("instinct",100));assertEquals(1,s.stacks("instinct",121));
    for(int i=0;i<20;i++)s.gain("instinct",5,120,125+i);
    assertEquals(5,s.stacks("instinct",150));assertEquals(0,s.stacks("instinct",300));
  }
  @Test void closingACastOwnerInvalidatesEveryDelayedPhaseAndRecast() {
    var s=new HeroPowerState();int generation=s.generation();s.flag("flight",100,0);
    s.openRecast("COCOON",30,0);assertTrue(s.recast("COCOON",29));
    assertFalse(s.recast("COCOON",29));s.openRecast("COCOON",30,30);
    s.clear();assertNotEquals(generation,s.generation());assertFalse(s.active("flight",31));
    assertFalse(s.recast("COCOON",31));
  }
  @Test void recastDeadlinesDoNotRefreshWhenInspected() {
    var s=new HeroPowerState();s.openRecast("WHIP",40,0);
    assertTrue(s.canRecast("WHIP",39));assertFalse(s.canRecast("WHIP",40));
    assertFalse(s.recast("WHIP",41));
  }
}
