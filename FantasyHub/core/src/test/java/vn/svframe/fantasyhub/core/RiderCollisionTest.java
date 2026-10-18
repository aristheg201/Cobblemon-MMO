package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

class RiderCollisionTest {
  @Test
  void dimensionKickCanHitWhenTheNextStepStartsInsideExpandedTarget() {
    var target = new Box(-.3, 0, 2.9, .3, 1.9, 3.5).expand(1.5);
    var start = new Vec3d(0, 1, 1.5);
    assertTrue(target.contains(start));
    assertEquals(start, RiderCollision.contact(target, start, new Vec3d(0, 1, 2.5)).orElseThrow());
  }

  @Test
  void projectilesSpawnedInsideLargeTargetsRegisterOneImmediateContact() {
    var body = new Box(-2, -2, -2, 2, 2, 2);
    assertEquals(
        Vec3d.ZERO, RiderCollision.contact(body, Vec3d.ZERO, new Vec3d(0, 0, 1)).orElseThrow());
  }

  @Test
  void ordinaryCrossingAndCompleteMissRetainVanillaGeometry() {
    var body = new Box(0, 0, 0, 1, 2, 1);
    assertTrue(RiderCollision.contact(body, new Vec3d(0, 1, -1), new Vec3d(0, 1, 2)).isPresent());
    assertTrue(RiderCollision.contact(body, new Vec3d(3, 1, -1), new Vec3d(3, 1, 2)).isEmpty());
  }
}
