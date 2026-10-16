package vn.svframe.fantasyhub.core;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.entry.RegistryEntry;
import vn.svframe.fantasyhub.core.mixin.StatusEffectLeaseAccess;

/** Server-owned belt effects. Existing potion effects are never overwritten to acquire a lease. */
final class ZecterPassives {
  private final Set<RegistryEntry<StatusEffect>> owned = new HashSet<>();

  void tick(LivingEntity player, boolean regeneration, boolean nightVision) {
    update(player, StatusEffects.REGENERATION, regeneration);
    update(player, StatusEffects.NIGHT_VISION, nightVision);
  }

  private void update(LivingEntity player, RegistryEntry<StatusEffect> type, boolean enabled) {
    StatusEffectInstance current = player.getStatusEffect(type);
    if (!enabled) {
      if (owned.contains(type) && current != null) {
        // Vanilla copies a weaker effect into hiddenEffect when a stronger potion arrives.
        // Removing only the current root would otherwise let our infinite lease reappear later.
        StatusEffectInstance cursor = current;
        while (cursor != null) {
          StatusEffectLeaseAccess access = (StatusEffectLeaseAccess)cursor;
          StatusEffectInstance next = access.fantasyhub$hidden();
          while (next != null && beltLease(next)) next = ((StatusEffectLeaseAccess)next).fantasyhub$hidden();
          access.fantasyhub$hidden(next);
          cursor = next;
        }
        if (beltLease(current)) player.removeStatusEffect(type);
      }
      owned.remove(type);
      return;
    }
    if (current == null) {
      // Infinite duration avoids Night Vision's last-ten-seconds flashing.
      player.addStatusEffect(new StatusEffectInstance(type, -1, 0, true, false, true));
      owned.add(type);
    }
  }

  private static boolean beltLease(StatusEffectInstance effect) {
    return effect.isInfinite() && effect.getAmplifier() == 0 && effect.isAmbient()
        && !effect.shouldShowParticles();
  }

  void clear(LivingEntity player) {
    update(player, StatusEffects.REGENERATION, false);
    update(player, StatusEffects.NIGHT_VISION, false);
  }
}
