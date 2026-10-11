package vn.svframe.svframemmo.cobblemon.move;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RealtimeBattleState {
   private final Map<UUID, EnumMap<BattleStat, RealtimeBattleState.Stage>> states = new ConcurrentHashMap<>();
   private final Map<UUID, Long> protectedUntil = new ConcurrentHashMap<>();

   public int stage(UUID entity, BattleStat stat, long tick) {
      EnumMap<BattleStat, RealtimeBattleState.Stage> map = this.states.get(entity);
      if (map == null) {
         return 0;
      } else {
         RealtimeBattleState.Stage stage = map.get(stat);
         if (stage == null) {
            return 0;
         } else if (tick >= stage.expiresAt()) {
            map.remove(stat);
            if (map.isEmpty()) {
               this.states.remove(entity, map);
            }

            return 0;
         } else {
            return stage.value();
         }
      }
   }

   public int add(UUID entity, BattleStat stat, int amount, long tick, long durationTicks) {
      if (amount == 0) {
         return this.stage(entity, stat, tick);
      } else {
         int current = this.stage(entity, stat, tick);
         EnumMap<BattleStat, RealtimeBattleState.Stage> map = this.states.computeIfAbsent(entity, ignored -> new EnumMap<>(BattleStat.class));
         int next = Math.max(-6, Math.min(6, current + amount));
         map.put(stat, new RealtimeBattleState.Stage(next, tick + Math.max(1L, durationTicks)));
         return next;
      }
   }

   public void protect(UUID entity, long tick, long durationTicks) {
      if (entity != null) {
         this.protectedUntil.put(entity, tick + Math.max(1L, durationTicks));
      }
   }

   public boolean protectedNow(UUID entity, long tick) {
      Long expiry = this.protectedUntil.get(entity);
      if (expiry == null) {
         return false;
      } else if (tick < expiry) {
         return true;
      } else {
         this.protectedUntil.remove(entity, expiry);
         return false;
      }
   }

   public void clear(UUID entity) {
      if (entity != null) {
         this.states.remove(entity);
         this.protectedUntil.remove(entity);
      }
   }

   public double damageMultiplier(UUID caster, UUID target, boolean physical, long tick) {
      BattleStat offense = physical ? BattleStat.ATTACK : BattleStat.SPECIAL_ATTACK;
      BattleStat defense = physical ? BattleStat.DEFENSE : BattleStat.SPECIAL_DEFENSE;
      return BattleStat.multiplier(this.stage(caster, offense, tick)) / BattleStat.multiplier(this.stage(target, defense, tick));
   }

   public double accuracyChance(UUID caster, UUID target, double accuracy, long tick) {
      if (accuracy <= 0.0) {
         return 1.0;
      } else {
         int stage = Math.max(-6, Math.min(6, this.stage(caster, BattleStat.ACCURACY, tick) - this.stage(target, BattleStat.EVASION, tick)));
         double multiplier = stage >= 0 ? (3.0 + stage) / 3.0 : 3.0 / (3.0 - stage);
         return Math.max(0.0, Math.min(1.0, accuracy / 100.0 * multiplier));
      }
   }

   public void clear() {
      this.states.clear();
      this.protectedUntil.clear();
   }

   public void tick(long tick) {
      this.protectedUntil.entrySet().removeIf(entry -> tick >= entry.getValue());
      this.states.forEach((id, map) -> {
         map.entrySet().removeIf(entry -> tick >= entry.getValue().expiresAt());
         if (map.isEmpty()) {
            this.states.remove(id, map);
         }
      });
   }

   private record Stage(int value, long expiresAt) {
   }
}
