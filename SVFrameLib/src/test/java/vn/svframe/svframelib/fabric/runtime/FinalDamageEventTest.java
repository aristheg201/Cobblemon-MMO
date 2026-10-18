package vn.svframe.svframelib.fabric.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.api.event.FinalDamageEvent;

class FinalDamageEventTest {
  @Test
  void stagesComposeOnceInOrderAndNeverConsumeAZeroDamageHit() {
    var order = new ArrayList<Integer>();
    FinalDamageEvent.EVENT.register(
        (target, source, amount) -> {
          order.add(1);
          return amount * .8f;
        });
    FinalDamageEvent.EVENT.register(
        (target, source, amount) -> {
          order.add(2);
          return amount - 2;
        });
    FinalDamageEvent.EVENT.register(
        (target, source, amount) -> {
          order.add(3);
          return Float.NaN;
        });
    assertEquals(6, FinalDamageEvent.EVENT.invoker().modify(null, null, 10), 1e-6);
    assertEquals(List.of(1, 2, 3), order);
    order.clear();
    assertEquals(0, FinalDamageEvent.EVENT.invoker().modify(null, null, 0));
    assertTrue(order.isEmpty());
    assertEquals(0, FinalDamageEvent.EVENT.invoker().modify(null, null, 1));
    assertEquals(List.of(1, 2), order);
  }
}
