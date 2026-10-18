package vn.svframe.fantasyhub.core.rider;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CardRiderStateTest {
  @Test
  void surviveIsExplicitlyToggledAndHumanResetRevokesEveryPower() {
    var state = new CardRiderState();
    assertFalse(state.toggle());
    state.henshin();
    assertTrue(state.toggle());
    assertTrue(state.elevated); // no tick-based expiry exists
    state.nextCards = true;
    state.barrier = 12;
    state.recastUntil = 300;
    state.clear();
    assertFalse(state.elevated);
    assertFalse(state.consumeCards());
    assertFalse(state.recast(0));
    assertEquals(5, state.absorb(5));
  }

  @Test
  void resonanceTriggersExactlyOncePerTenActualNormalFormHits() {
    var state = new CardRiderState();
    state.henshin();
    for (int i = 0; i < 29; i++) assertEquals(i % 10 == 9, state.resonance());
    assertEquals(9, state.flame());
    state.toggle();
    assertFalse(state.resonance());
    assertEquals(9, state.flame());
  }

  @Test
  void cardsAndRecastCannotBeReusedAndSpiritHasAnIndependentExpiry() {
    var state = new CardRiderState();
    state.henshin();
    state.nextCards = true;
    assertTrue(state.consumeCards());
    assertFalse(state.consumeCards());
    state.recastUntil = 40;
    assertTrue(state.recast(39));
    assertFalse(state.recast(39));
    state.recastUntil = 40;
    assertFalse(state.recast(40));
    assertEquals(1, state.cycleSpirit(100, 60));
    assertEquals(1, state.spirit(159));
    assertEquals(0, state.spirit(160));
    assertEquals(2, state.cycleSpirit(200, 60));
  }
}
