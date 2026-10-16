package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class KabutoStateTest {
  @Test
  void deactivationClearsTransformationAndClockWithoutRefillingMetersOrChangingWeaponMode() {
    var s = new KabutoState();
    s.transformed = true;
    s.form = KabutoState.Form.RIDER;
    s.hyper = true;
    s.gun = false;
    s.energy = 23;
    s.gauge = 41;
    s.combo = 7;
    s.clockUntil = 200;
    assertTrue(s.deactivate());
    assertFalse(s.transformed);
    assertFalse(s.hyper);
    assertFalse(s.clock(50));
    assertEquals(KabutoState.Form.MASKED, s.form);
    assertEquals(0, s.combo);
    assertEquals(23, s.energy);
    assertEquals(41, s.gauge);
    assertFalse(s.gun);
    assertFalse(s.deactivate());
  }

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
