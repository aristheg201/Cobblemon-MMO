package vn.svframe.svframelib.fabric.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.api.event.AttackEvent;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageMetadata;

class NativeAttackEventBridgeTest {
  private AttackMetadata attack() {
    return new AttackMetadata(
        new DamageMetadata(20, List.of(vn.svframe.svframelib.damage.DamageType.SKILL)), null, null);
  }

  @Test
  void canonicalEventRunsOnceAndPreservesBuffsAcrossVanillaScaling() {
    var attack = attack();
    var calls = new AtomicInteger();
    AttackEvent.EVENT.register(
        event -> {
          if (event.getAttack() != attack) return;
          assertTrue(RegisteredDamageMetadataBridge.dispatching());
          calls.incrementAndGet();
          event.getDamage().multiplicativeModifier(1.3).registerSkillCriticalStrike();
        });
    attack.beginApplication();
    double before = attack.getDamage().getDamage();
    assertTrue(RegisteredDamageMetadataBridge.fire(attack));
    assertTrue(RegisteredDamageMetadataBridge.fire(attack));
    assertEquals(1, calls.get());
    var nativeDamage =
        RegisteredDamageMetadataBridge.afterEvent(attack.getDamage(), before, 5, List.of());
    assertEquals(6.5, nativeDamage.damage(), 1e-9);
    assertTrue(nativeDamage.isSkillCriticalStrike());
    assertTrue(nativeDamage.hasType(DamageType.SKILL));
    assertFalse(RegisteredDamageMetadataBridge.dispatching());
  }

  @Test
  void cancellationCannotBeReportedAsAnAppliedHitAndNewAttemptsResetIt() {
    var attack = attack();
    var calls = new AtomicInteger();
    AttackEvent.EVENT.register(
        event -> {
          if (event.getAttack() == attack) {
            calls.incrementAndGet();
            event.setCancelled(true);
          }
        });
    attack.beginApplication();
    assertFalse(RegisteredDamageMetadataBridge.fire(attack));
    attack.recordApplication(true, 20);
    assertFalse(attack.wasApplied());
    assertEquals(0, attack.getAppliedDamage());
    assertFalse(RegisteredDamageMetadataBridge.fire(attack));
    assertEquals(1, calls.get());
    attack.beginApplication();
    assertFalse(attack.isCancelled());
    assertFalse(RegisteredDamageMetadataBridge.fire(attack));
    assertEquals(2, calls.get());
  }

  @Test
  void exceptionalListenersCannotLeakNativeDispatchContext() {
    var attack = attack();
    AttackEvent.EVENT.register(
        event -> {
          if (event.getAttack() == attack) throw new IllegalStateException("probe");
        });
    assertThrows(IllegalStateException.class, () -> RegisteredDamageMetadataBridge.fire(attack));
    assertFalse(RegisteredDamageMetadataBridge.dispatching());
  }
}
