package vn.svframe.worldboss;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class SnapSpinTest {
 @Test void recordedOutcomeReachesCenterWithoutReroll(){int[] row=new int[9];for(int i=0;i<60;i++)SnapSpin.advance(row,i,7,2);assertEquals(7,row[4]);}
 @Test void slowdownIsBoundedAndMonotonic(){long prior=0;for(int i=0;i<60;i++){long delay=SnapSpin.delay(i);assertTrue(delay>=prior&&delay>=1&&delay<=4);prior=delay;}}
}
