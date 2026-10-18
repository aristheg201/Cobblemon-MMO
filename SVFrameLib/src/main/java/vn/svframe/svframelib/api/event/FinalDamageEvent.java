package vn.svframe.svframelib.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

/**
 * Server damage after shared RPG calculation. Extensions must not compete for the vanilla return.
 */
public final class FinalDamageEvent {
  private FinalDamageEvent() {}

  @FunctionalInterface
  public interface Listener {
    float modify(LivingEntity target, DamageSource source, float amount);
  }

  public static final Event<Listener> EVENT =
      EventFactory.createArrayBacked(
          Listener.class,
          listeners ->
              (target, source, amount) -> {
                float result = amount;
                for (var listener : listeners) {
                  if (result <= 0) return 0;
                  float next = listener.modify(target, source, result);
                  if (Float.isFinite(next)) result = Math.max(0, next);
                }
                return result;
              });
}
