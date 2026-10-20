package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class HeroTimelineTest {
  @Test void delayedHitsStopAfterOwnerGenerationChanges() {
    var t=new HeroTimeline();var owner=UUID.randomUUID();int[] generation={1};int[] damage={0};
    t.schedule(owner,5,()->generation[0]==1,()->damage[0]+=10);
    t.schedule(owner,10,()->generation[0]==1,()->damage[0]+=10);
    t.tick(5);assertEquals(10,damage[0]);generation[0]++;t.tick(10);assertEquals(10,damage[0]);assertEquals(0,t.pending());
  }
  @Test void sameTickStepsRetainTheirCastOrderAndCleanupIsOwnerScoped() {
    var t=new HeroTimeline();var a=UUID.randomUUID();var b=UUID.randomUUID();var result=new ArrayList<Integer>();
    t.schedule(a,4,()->true,()->result.add(1));t.schedule(b,4,()->true,()->result.add(2));
    t.schedule(a,4,()->true,()->result.add(3));t.cancel(a);t.tick(4);
    assertEquals(List.of(2),result);
  }
  @Test void scheduleLimitIsPerOwnerAndDoesNotDropPreviouslyAcceptedSteps() {
    var t=new HeroTimeline();var owner=UUID.randomUUID();int[] ran={0};
    for(int i=0;i<128;i++)assertTrue(t.schedule(owner,10,()->true,()->ran[0]++));
    assertFalse(t.schedule(owner,10,()->true,()->ran[0]++));t.tick(10);assertEquals(128,ran[0]);
    assertEquals(0,t.pending());
  }
}
