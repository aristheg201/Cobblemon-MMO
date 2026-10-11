package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record CobblemonMoveProfile(
   String moveId,
   String type,
   String damageCategory,
   String targetType,
   CobblemonMoveProfile.Executor executor,
   double baseDamage,
   double range,
   double radius,
   double cooldownSeconds,
   double dashStrength
) {
   private static final Set<String> HEAL_MOVES = Set.of(
      "recover",
      "roost",
      "slackoff",
      "softboiled",
      "milkdrink",
      "healorder",
      "shoreup",
      "synthesis",
      "moonlight",
      "morningsun",
      "lifedew",
      "junglehealing",
      "lunarblessing",
      "healbell",
      "rest"
   );
   private static final Set<String> SHIELD_MOVES = Set.of(
      "protect",
      "detect",
      "kingsshield",
      "spikyshield",
      "banefulbunker",
      "obstruct",
      "silktrap",
      "burningbulwark",
      "matblock",
      "wideguard",
      "quickguard",
      "craftyshield",
      "maxguard"
   );
   private static final Map<String, String> WEATHER_MOVES = Map.of("raindance", "rain", "sunnyday", "clear");
   private static final Set<String> TELEPORT_MOVES = Set.of("teleport");
   private static final Set<String> DASH_MOVES = Set.of(
      "quickattack", "extremespeed", "aquajet", "bulletpunch", "iceshard", "shadowsneak", "suckerpunch", "machpunch", "vacuumwave", "accelerock"
   );

   public static CobblemonMoveProfile of(MoveTemplate move) {
      String id = CobblemonMoveSkillAdapter.id(move.getName());
      String type = move.getElementalType().getName().toLowerCase(Locale.ROOT);
      String category = move.getDamageCategory().getName().toLowerCase(Locale.ROOT);
      String target = move.getTarget().name().toLowerCase(Locale.ROOT);
      return fromProvider(id, type, category, target, move.getPower(), move.getPriority());
   }

   public static CobblemonMoveProfile fromProvider(String id, String type, String category, String target, double power, int priority) {
      CobblemonMoveProfile.Executor executor = executor(id, category, target);
      long cooldownMs = Math.max(500L, Math.min(30000L, 1500L + (long)Math.max(0.0, power) * 15L - priority * 150L));
      if (executor == CobblemonMoveProfile.Executor.HEAL
         || executor == CobblemonMoveProfile.Executor.SHIELD
         || executor == CobblemonMoveProfile.Executor.WEATHER) {
         cooldownMs = Math.max(cooldownMs, 8000L);
      }

      double baseDamage = power > 0.0 ? Math.max(1.0, power / 10.0) : 0.0;
      double radius = executor == CobblemonMoveProfile.Executor.AOE ? 5.0 : 0.0;

      double range = switch (executor) {
         case HEAL, SHIELD, WEATHER, SELF_BUFF -> 0.0;
         case TELEPORT -> 12.0;
         default -> 18.0;
         case AOE -> radius;
      };
      if (id.equals("bite")) {
         range = 3.5;
      } else if (id.equals("lick")) {
         range = 6.0;
      }

      double dash = Math.min(2.0, 0.8 + Math.max(0, priority) * 0.15);
      return new CobblemonMoveProfile(id, type, category, target, executor, baseDamage, range, radius, cooldownMs / 1000.0, dash);
   }

   public boolean isHeal() {
      return HEAL_MOVES.contains(this.moveId);
   }

   public boolean isShield() {
      return SHIELD_MOVES.contains(this.moveId);
   }

   public String weather() {
      return WEATHER_MOVES.get(this.moveId);
   }

   public boolean cleanse() {
      return this.moveId.equals("healbell") || this.moveId.equals("junglehealing") || this.moveId.equals("lunarblessing");
   }

   public double healBase() {
      return !this.isHeal() ? 0.0 : (this.moveId.equals("rest") ? 12.0 : 8.0);
   }

   public boolean requiresSingleTarget() {
      return this.executor == CobblemonMoveProfile.Executor.TARGET
         || this.executor == CobblemonMoveProfile.Executor.PROJECTILE
         || this.executor == CobblemonMoveProfile.Executor.TARGET_DEBUFF;
   }

   public String fallbackStatus() {
      String var1 = this.type;

      return switch (var1) {
         case "poison" -> "minecraft:poison";
         case "ice", "water", "electric", "grass" -> "minecraft:slowness";
         default -> "minecraft:weakness";
      };
   }

   private static CobblemonMoveProfile.Executor executor(String id, String category, String target) {
      if (HEAL_MOVES.contains(id)) {
         return CobblemonMoveProfile.Executor.HEAL;
      } else if (SHIELD_MOVES.contains(id)) {
         return CobblemonMoveProfile.Executor.SHIELD;
      } else if (WEATHER_MOVES.containsKey(id)) {
         return CobblemonMoveProfile.Executor.WEATHER;
      } else if (TELEPORT_MOVES.contains(id)) {
         return CobblemonMoveProfile.Executor.TELEPORT;
      } else if (DASH_MOVES.contains(id)) {
         return CobblemonMoveProfile.Executor.DASH;
      } else if (target.equals("all") || target.contains("alladjacent")) {
         return CobblemonMoveProfile.Executor.AOE;
      } else if (target.contains("self") || target.contains("allyside") || target.contains("allyteam")) {
         return CobblemonMoveProfile.Executor.SELF_BUFF;
      } else if (category.equals("status")) {
         return CobblemonMoveProfile.Executor.TARGET_DEBUFF;
      } else {
         return category.equals("physical") ? CobblemonMoveProfile.Executor.TARGET : CobblemonMoveProfile.Executor.PROJECTILE;
      }
   }

   public static enum Executor {
      HEAL,
      SHIELD,
      WEATHER,
      TELEPORT,
      DASH,
      AOE,
      SELF_BUFF,
      TARGET_DEBUFF,
      TARGET,
      PROJECTILE;
   }
}
