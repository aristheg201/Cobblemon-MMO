package vn.svframe.svframemobs.mythic;

import org.junit.jupiter.api.Test;
import static vn.svframe.svframemobs.mythic.MythicEngine.Point;
import static org.junit.jupiter.api.Assertions.*;

class PullMotionTest {
    @Test void sourceBlackholeUnitsPullTowardsItsElevatedOriginWithDistanceFalloff() {
        var motion=PullMotion.towards(new Point("world",10,2,0),new Point("world",0,0,0),5.8);
        assertEquals(-1.682,motion.x(),1e-10);assertEquals(-.228752,motion.y(),1e-10);assertEquals(0,motion.z());
        var near=PullMotion.towards(new Point("world",.1,0,0),new Point("world",0,0,0),5.8);
        assertEquals(-.01682,near.x(),1e-10);
    }
    @Test void coincidentOriginsStayFiniteAndLargePullsRetainTheSourceFourBlockCap() {
        var point=new Point("world",0,0,0);
        assertEquals(new PullMotion.Motion(0,0,0),PullMotion.towards(point,point,5.8));
        var capped=PullMotion.towards(point,new Point("world",100,0,0),10);
        assertEquals(new PullMotion.Motion(4,0,0),capped);
        assertThrows(IllegalArgumentException.class,()->PullMotion.towards(point,new Point("nether",0,0,0),5));
        assertThrows(IllegalArgumentException.class,()->PullMotion.towards(point,point,Double.NaN));
    }
}
