package vn.svframe.fantasyhub.client;
import org.junit.jupiter.api.Test;
import net.minecraft.util.math.Vec3d;
import static org.junit.jupiter.api.Assertions.*;
class HeroMotionHistoryTest {
 @Test void capturesActualPositionsOncePerTickAndBoundsHistory(){var h=new HeroMotionHistory();for(int i=0;i<20;i++){h.add(new Vec3d(i*.4,0,0),0,i);h.add(new Vec3d(i*.4+.1,0,0),0,i);}assertEquals(6,h.samples().size());assertEquals(7.2,h.samples().getFirst().at().x,.001);assertTrue(h.samples().stream().allMatch(s->s.at().x<7.6));}
 @Test void standingAndTeleportClearRatherThanStackGhosts(){var h=new HeroMotionHistory();h.add(Vec3d.ZERO,0,0);h.add(new Vec3d(1,0,0),0,1);assertEquals(1,h.samples().size());h.add(new Vec3d(1,0,0),0,2);assertTrue(h.samples().isEmpty());h.add(new Vec3d(2,0,0),0,3);h.add(new Vec3d(100,0,0),0,4);assertTrue(h.samples().isEmpty());assertEquals(0,h.speed());}
}
