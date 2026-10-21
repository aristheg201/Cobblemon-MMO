package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AxeFlightMathTest {
 @Test void throwStopsAtTwentyBlocks(){assertEquals(20,AxeFlightMath.distance(100));assertEquals(3,AxeFlightMath.distance(3));assertEquals(0,AxeFlightMath.distance(-1));}
 @Test void sweptPathCatchesTargetsBetweenTicksButNotBesidePath(){assertTrue(AxeFlightMath.touches(0,0,0,2,0,0,1,.8,0,.9));assertFalse(AxeFlightMath.touches(0,0,0,2,0,0,1,1.1,0,.9));assertFalse(AxeFlightMath.touches(0,0,0,2,0,0,4,0,0,.9));}
 @Test void sameTargetCanBeHitOnceInEachDirection(){var hits=new AxeFlightMath.Hits();assertTrue(hits.accept("a",false));assertFalse(hits.accept("a",false));assertTrue(hits.accept("a",true));assertFalse(hits.accept("a",true));for(int i=0;i<30;i++)assertTrue(hits.accept("target"+i,false));}
}
