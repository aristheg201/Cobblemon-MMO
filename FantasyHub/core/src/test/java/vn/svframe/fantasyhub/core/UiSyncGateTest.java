package vn.svframe.fantasyhub.core;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class UiSyncGateTest {
 @Test void closedHudNeverSchedulesCatalogWork(){var g=new UiSyncGate();for(int tick=0;tick<2400;tick++)assertNull(g.next(tick));}
 @Test void requestsCoalesceAndOnlyOnePlayerBuildsPerDispatch(){var g=new UiSyncGate();var a=UUID.randomUUID();var b=UUID.randomUUID();g.open(a,"shop",0);g.open(a,"classes",0);g.open(b,"pokemon",0);assertEquals(new UiSyncGate.Dispatch(a,"classes"),g.next(0));assertEquals(new UiSyncGate.Dispatch(b,"pokemon"),g.next(1));assertNull(g.next(2));}
 @Test void closeAndExpiryRemoveSubscriptionsButDoNotOpenOtherMenus(){var g=new UiSyncGate();var p=UUID.randomUUID();g.open(p,"shop",0);g.close(p,"profile");assertEquals("shop",g.scope(p,1));g.close(p,"shop");assertNull(g.next(1));g.open(p,"upgrades",2);assertNull(g.scope(p,103));assertNull(g.next(103));}
 @Test void changedContextUpdatesOnceAndSpamCannotBypassBuildInterval(){var g=new UiSyncGate();var p=UUID.randomUUID();g.open(p,"shop",0);g.next(0);for(int t=1;t<10;t++){g.dirty(p);assertNull(g.next(t));}assertNotNull(g.next(10));assertNull(g.next(11));}
}
