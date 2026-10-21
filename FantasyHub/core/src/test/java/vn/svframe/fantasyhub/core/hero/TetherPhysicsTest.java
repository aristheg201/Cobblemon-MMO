package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class TetherPhysicsTest {
 @Test void ropeDoesNotPushWhenSlack(){assertArrayEquals(new double[]{.3,-.08,0},TetherPhysics.velocity(0,0,0,0,10,0,12,.3,-.08,0,false),1e-9);}
 @Test void tensionPullsUpWhileKeepingTangentialMotion(){var v=TetherPhysics.velocity(0,0,0,0,10,0,8,.3,-.08,0,false);assertEquals(.3,v[0],1e-9);assertTrue(v[1]>0);}
 @Test void reelHasFloorAndNeverLengthensRope(){assertEquals(2,TetherPhysics.reel(2));assertTrue(TetherPhysics.reel(10)<10);}
 @Test void releaseIsNotAnAppliedVelocityStep(){assertFalse(TetherPhysics.attached(true,false,true));assertFalse(TetherPhysics.attached(false,true,true));assertTrue(TetherPhysics.attached(false,false,true));}
}
