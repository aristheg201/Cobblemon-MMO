package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class UiSyncGateTest {
 @Test void closedGuiHasNoSubscription(){var g=new UiSyncGate();assertNull(g.scope(UUID.randomUUID(),0));}
 @Test void latestScopeAndHeartbeatAreRetained(){var g=new UiSyncGate();var p=UUID.randomUUID();assertTrue(g.open(p,"shop",0));g.open(p,"classes",1);assertEquals("classes",g.scope(p,2));g.touch(p,90);assertEquals("classes",g.scope(p,189));assertNull(g.scope(p,190));}
 @Test void staleCloseCannotCancelNewScreen(){var g=new UiSyncGate();var p=UUID.randomUUID();g.open(p,"shop",0);g.open(p,"upgrades",1);assertFalse(g.close(p,"shop"));assertEquals("upgrades",g.scope(p,2));assertTrue(g.close(p,"upgrades"));assertNull(g.scope(p,3));}
 @Test void unknownScopeAndDisconnectCannotLeakSubscriptions(){var g=new UiSyncGate();var p=UUID.randomUUID();assertFalse(g.open(p,"bad",0));g.open(p,"profile",0);g.remove(p);assertNull(g.scope(p,1));g.open(p,"profile",2);g.clear();assertNull(g.scope(p,3));}
}
