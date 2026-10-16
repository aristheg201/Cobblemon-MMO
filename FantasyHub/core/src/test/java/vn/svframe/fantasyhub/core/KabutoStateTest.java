package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KabutoStateTest {
  @Test
  void clockRequiresRiderAndEnoughGaugeAndExpiresExactlyAfterFiveSeconds() {
    var s = new KabutoState();
    s.gain(0, 100);
    assertFalse(s.startClock(20, 100, 100));
    assertEquals(100, s.gauge);
    s.form = KabutoState.Form.RIDER;
    s.gauge = 99;
    assertFalse(s.startClock(20, 100, 100));
    assertEquals(99, s.gauge);
    s.gauge = 100;
    assertTrue(s.startClock(20, 100, 100));
    assertEquals(0, s.gauge);
    assertTrue(s.clock(119));
    assertFalse(s.clock(120));
    assertFalse(s.startClock(30, 100, 100));
  }

  @Test
  void empoweredSpecialConsumesOnlyOneFullMeterAndProvidesComboPoints() {
    var s = new KabutoState();
    s.gain(95, 95);
    assertFalse(s.consumeEmpowerment());
    assertEquals(95, s.energy);
    s.gain(20, 20);
    assertEquals(100, s.energy);
    assertEquals(100, s.gauge);
    assertTrue(s.consumeEmpowerment());
    assertEquals(0, s.energy);
    assertEquals(2, s.combo);
    assertFalse(s.consumeEmpowerment());
    assertEquals(2, s.combo);
  }
}
