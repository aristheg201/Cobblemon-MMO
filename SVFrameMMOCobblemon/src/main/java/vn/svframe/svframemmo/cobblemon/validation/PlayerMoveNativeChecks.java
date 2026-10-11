package vn.svframe.svframemmo.cobblemon.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import vn.svframe.svframemmo.cobblemon.move.BattleStat;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveProfile;
import vn.svframe.svframemmo.cobblemon.move.MoveSemantic;
import vn.svframe.svframemmo.cobblemon.move.MoveSemanticRegistry;
import vn.svframe.svframemmo.cobblemon.move.OverworldMoveMath;
import vn.svframe.svframemmo.cobblemon.move.PlayerMoveCoverage;
import vn.svframe.svframemmo.cobblemon.move.RealtimeBattleState;

public final class PlayerMoveNativeChecks {
   private PlayerMoveNativeChecks() {
   }

   public static void run(String[] args) {
      Set<String> catalog = PlayerMoveCoverage.purchaseAdapters();
      check(catalog.size() == 155, "Expanded player catalog must retain 155 explicit adapters");

      for (String id : new String[]{"bite", "lick", "flamethrower", "icebeam", "thunderbolt", "surf", "earthquake", "gigadrain", "protect", "teleport"}) {
         check(PlayerMoveCoverage.purchasable(id), "Missing signature or core player move: " + id);
      }

      for (String id : new String[]{"metronome", "sketch", "fissure", "counter", "destinybond", "transform", "suckerpunch"}) {
         check(!PlayerMoveCoverage.purchasable(id), "Unhandled move must remain unavailable: " + id);
      }

      CobblemonMoveProfile bite = CobblemonMoveProfile.fromProvider("bite", "dark", "physical", "normal", 60.0, 0);
      CobblemonMoveProfile lick = CobblemonMoveProfile.fromProvider("lick", "ghost", "physical", "normal", 30.0, 0);
      check(close(bite.range(), 3.5) && close(lick.range(), 6.0) && bite.requiresSingleTarget() && lick.requiresSingleTarget(), "Signature attack ranges");
      CobblemonMoveProfile dash = CobblemonMoveProfile.fromProvider("quickattack", "normal", "physical", "normal", 40.0, 1);
      check(dash.executor() == CobblemonMoveProfile.Executor.DASH && !dash.requiresSingleTarget(), "Dash must work without a target");
      CobblemonMoveProfile growl = CobblemonMoveProfile.fromProvider("growl", "normal", "status", "alladjacentfoes", 0.0, 0);
      check(
         growl.executor() == CobblemonMoveProfile.Executor.AOE && growl.baseDamage() == 0.0 && growl.healBase() == 0.0 && close(growl.range(), growl.radius()),
         "Area debuffs must not manufacture damage, healing or a larger range"
      );
      check(close(OverworldMoveMath.healing(100.0, 0.5, 8.0, 8.0), 50.0), "Base fractional recovery");
      check(close(OverworldMoveMath.healing(100.0, 0.5, 11.6, 8.0), 72.5), "Recovery upgrades must affect fractional healing");
      check(close(OverworldMoveMath.healing(100.0, 0.5, 40.0, 8.0), 100.0), "Recovery must cap at maximum health");
      RealtimeBattleState state = new RealtimeBattleState();
      UUID actor = UUID.randomUUID();
      UUID target = UUID.randomUUID();
      state.add(actor, BattleStat.ATTACK, 2, 0L, 600L);
      check(
         close(state.damageMultiplier(actor, target, true, 1L), 2.0) && close(state.damageMultiplier(actor, target, false, 1L), 1.0),
         "Physical and special stages must remain distinct"
      );
      state.add(target, BattleStat.DEFENSE, 2, 0L, 600L);
      check(close(state.damageMultiplier(actor, target, true, 1L), 1.0), "Defense stages must reduce move damage");
      state.add(actor, BattleStat.ATTACK, 1, 600L, 600L);
      check(state.stage(actor, BattleStat.ATTACK, 601L) == 1, "Reapplying an expired stage must retain the new map");
      check(state.stage(actor, BattleStat.ATTACK, 1200L) == 0, "Stage expiry");
      state.add(actor, BattleStat.SPECIAL_ATTACK, 30, 0L, 600L);
      check(state.stage(actor, BattleStat.SPECIAL_ATTACK, 1L) == 6, "Stage cap");
      state.add(actor, BattleStat.ACCURACY, -1, 0L, 600L);
      check(
         close(state.accuracyChance(actor, target, 100.0, 1L), 0.75) && close(state.accuracyChance(actor, target, 0.0, 1L), 1.0),
         "Accuracy debuffs and always-hit moves"
      );
      state.protect(actor, 0L, 20L);
      check(state.protectedNow(actor, 19L) && !state.protectedNow(actor, 20L), "Guard duration");
      state.clear();
      check(state.stage(actor, BattleStat.ACCURACY, 1L) == 0, "Stage cleanup");
      System.out.println("PLAYER_MOVE_MODELS=PASS");
      if (args.length == 0) {
         System.out.println("PLAYER_MOVE_PROVIDER_SEMANTICS=NOT_RUN (supply -PmoveProviderAudit=path)");
      } else {
         try {
            JsonObject audit = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
            check(audit.get("count").getAsInt() == catalog.size(), "Provider audit coverage count");
            MoveSemanticRegistry semantics = new MoveSemanticRegistry();
            HashSet<String> seen = new HashSet<>();
            HashSet<String> types = new HashSet<>();
            ArrayList<String> failures = new ArrayList<>();

            for (JsonElement raw : audit.getAsJsonArray("moves")) {
               try {
                  JsonObject row = raw.getAsJsonObject();
                  String id = row.get("id").getAsString();
                  seen.add(id);
                  types.add(row.get("type").getAsString());
                  check(catalog.contains(id), "Provider audit contains an unsold move: " + id);
                  MoveSemantic semantic = semantics.resolveKnown(id);
                  check(close(semantic.healFraction(), row.get("heal").getAsDouble()), id + " healing fraction");
                  check(close(semantic.drainFraction(), row.get("drain").getAsDouble()), id + " drain fraction");
                  check(Math.abs(semantic.recoilFraction() - row.get("recoil").getAsDouble()) < 0.005, id + " recoil fraction");
                  check(
                     semantic.multiHitMin() == row.getAsJsonArray("multi").get(0).getAsInt()
                        && semantic.multiHitMax() == row.getAsJsonArray("multi").get(1).getAsInt(),
                     id + " hit count"
                  );
                  String status = row.get("status").getAsString();

                  MoveSemantic.Status expected = switch (status) {
                     case "par" -> MoveSemantic.Status.PARALYSIS;
                     case "brn" -> MoveSemantic.Status.BURN;
                     case "psn" -> MoveSemantic.Status.POISON;
                     case "tox" -> MoveSemantic.Status.BAD_POISON;
                     case "slp" -> MoveSemantic.Status.SLEEP;
                     case "frz" -> MoveSemantic.Status.FREEZE;
                     case "confusion" -> MoveSemantic.Status.CONFUSION;
                     case "flinch" -> MoveSemantic.Status.FLINCH;
                     default -> MoveSemantic.Status.NONE;
                  };
                  check(semantic.status() == expected, id + " status effect");
                  if (expected != MoveSemantic.Status.NONE) {
                     check(close(semantic.statusChance(), row.get("statusChance").getAsDouble()), id + " status chance");
                  }

                  if (status.equals("protect")) {
                     check(semantic.protect(), id + " guard effect");
                  }

                  JsonArray changes = row.getAsJsonArray("stages");
                  check(changes.size() == semantic.stages().size(), id + " stage count");

                  for (JsonElement change : changes) {
                     JsonObject c = change.getAsJsonObject();
                     check(
                        semantic.stages()
                           .stream()
                           .anyMatch(
                              s -> s.target().name().equals(c.get("target").getAsString())
                                 && s.stat().name().equals(c.get("stat").getAsString())
                                 && s.stages() == c.get("stages").getAsInt()
                                 && close(s.chance(), c.get("chance").getAsDouble())
                           ),
                        id + " stage semantics"
                     );
                  }

                  CobblemonMoveProfile profile = CobblemonMoveProfile.fromProvider(
                     id,
                     row.get("type").getAsString(),
                     row.get("category").getAsString(),
                     row.get("target").getAsString(),
                     row.get("power").getAsDouble(),
                     row.get("priority").getAsInt()
                  );
                  if (row.get("power").getAsDouble() == 0.0) {
                     check(profile.baseDamage() == 0.0, id + " status damage");
                  }
               } catch (IllegalStateException var25) {
                  failures.add(var25.getMessage());
               }
            }

            check(failures.isEmpty(), String.join("; ", failures));
            check(seen.equals(catalog) && types.size() == 18, "All player adapters must exist across all 18 provider types");
            System.out.println("PLAYER_MOVE_PROVIDER_SEMANTICS=PASS moves=" + seen.size() + " types=" + types.size());
         } catch (IOException var26) {
            throw new IllegalStateException("Cannot read move provider audit", var26);
         }
      }
   }

   private static boolean close(double a, double b) {
      return Math.abs(a - b) < 1.0E-9;
   }

   private static void check(boolean condition, String message) {
      if (!condition) {
         throw new IllegalStateException(message);
      }
   }
}
