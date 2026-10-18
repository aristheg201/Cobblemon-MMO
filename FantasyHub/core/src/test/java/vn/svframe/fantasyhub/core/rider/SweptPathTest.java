package vn.svframe.fantasyhub.core.rider;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SweptPathTest {
  @Test
  void clearEndpointNeverAllowsCrossingAnOccupiedMiddle() {
    double fraction = SweptPath.clearFraction(24, t -> t < .4 || t > .5);
    assertTrue(fraction < .4);
    assertTrue(fraction >= .39);
  }

  @Test
  void aThinBodyObstacleStopsTravelBeforeIt() {
    double fraction = SweptPath.clearFraction(12, t -> Math.abs(t * 12 - 4) > .08);
    assertTrue(fraction * 12 < 4);
  }

  @Test
  void invalidPathsCannotTriggerUnboundedQueries() {
    assertEquals(0, SweptPath.clearFraction(Double.NaN, t -> fail("invalid probe")));
    assertEquals(0, SweptPath.clearFraction(65, t -> fail("invalid probe")));
    assertEquals(1, SweptPath.clearFraction(6, t -> true));
  }
}
