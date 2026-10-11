package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MoveSemanticRegistry {
   private final Map<String, MoveSemantic> known = new HashMap<>();

   public MoveSemanticRegistry() {
      this.selfStage(BattleStat.ATTACK, 2, "swordsdance");
      this.selfStage(BattleStat.SPECIAL_ATTACK, 2, "nastyplot");
      this.selfStages(List.of(sc(BattleStat.SPECIAL_ATTACK, 1), sc(BattleStat.SPECIAL_DEFENSE, 1)), "calmmind");
      this.selfStages(List.of(sc(BattleStat.ATTACK, 1), sc(BattleStat.SPEED, 1)), "dragondance");
      this.selfStages(List.of(sc(BattleStat.SPECIAL_ATTACK, 1), sc(BattleStat.SPECIAL_DEFENSE, 1), sc(BattleStat.SPEED, 1)), "quiverdance");
      this.selfStages(List.of(sc(BattleStat.ATTACK, 1), sc(BattleStat.DEFENSE, 1)), "bulkup");
      this.selfStages(List.of(sc(BattleStat.ATTACK, 1), sc(BattleStat.SPECIAL_ATTACK, 1)), "workup");
      this.selfStage(BattleStat.DEFENSE, 2, "irondefense", "acidarmor", "barrier");
      this.selfStage(BattleStat.DEFENSE, 3, "cottonguard");
      this.selfStage(BattleStat.DEFENSE, 1, "harden", "defensecurl", "withdraw");
      this.selfStage(BattleStat.SPECIAL_DEFENSE, 2, "amnesia");
      this.selfStage(BattleStat.SPEED, 2, "agility", "rockpolish");
      this.targetStage(BattleStat.DEFENSE, -1, "tailwhip", "leer");
      this.targetStage(BattleStat.ATTACK, -1, "growl");
      this.targetStage(BattleStat.SPEED, -2, "scaryface", "stringshot");
      this.targetStage(BattleStat.ACCURACY, -1, "sandattack", "smokescreen", "kinesis");
      this.status(MoveSemantic.Status.PARALYSIS, 1.0, "thunderwave", "glare", "stunspore");
      this.status(MoveSemantic.Status.BURN, 1.0, "willowisp");
      this.status(MoveSemantic.Status.POISON, 1.0, "poisonpowder");
      this.status(MoveSemantic.Status.BAD_POISON, 1.0, "toxic");
      this.status(MoveSemantic.Status.SLEEP, 1.0, "spore", "sleeppowder", "hypnosis", "sing", "lovelykiss");
      this.status(MoveSemantic.Status.CONFUSION, 1.0, "confuseray", "supersonic", "sweetkiss");
      this.status(MoveSemantic.Status.PARALYSIS, 0.3, "lick");
      this.status(MoveSemantic.Status.FLINCH, 0.3, "bite", "headbutt", "rockslide", "ironhead", "zenheadbutt", "airslash");
      this.status(MoveSemantic.Status.FLINCH, 1.0, "fakeout");
      this.status(MoveSemantic.Status.BURN, 0.1, "flamethrower", "firepunch", "ember", "heatwave");
      this.status(MoveSemantic.Status.PARALYSIS, 0.1, "thunderbolt", "thunderpunch");
      this.status(MoveSemantic.Status.POISON, 0.3, "sludgebomb", "poisonjab");
      this.status(MoveSemantic.Status.FREEZE, 0.1, "icebeam", "icepunch", "blizzard");
      this.status(MoveSemantic.Status.BURN, 0.1, "fireblast");
      this.status(MoveSemantic.Status.BURN, 0.3, "scald");
      this.status(MoveSemantic.Status.PARALYSIS, 0.1, "thundershock");
      this.status(MoveSemantic.Status.PARALYSIS, 0.3, "thunder", "discharge", "dragonbreath", "spark");
      this.status(MoveSemantic.Status.FREEZE, 0.1, "powdersnow");
      this.status(MoveSemantic.Status.POISON, 0.3, "poisonsting", "sludge");
      this.status(MoveSemantic.Status.CONFUSION, 0.1, "confusion", "psybeam");
      this.status(MoveSemantic.Status.CONFUSION, 0.2, "waterpulse");
      this.status(MoveSemantic.Status.FLINCH, 0.2, "darkpulse", "waterfall");
      this.status(MoveSemantic.Status.FLINCH, 0.1, "extrasensory");
      this.targetStageChance(BattleStat.SPECIAL_DEFENSE, -1, 0.1, "psychic", "flashcannon", "bugbuzz", "acid");
      this.targetStageChance(BattleStat.SPECIAL_DEFENSE, -1, 0.2, "shadowball");
      this.targetStageChance(BattleStat.SPECIAL_ATTACK, -1, 0.3, "moonblast");
      this.targetStage(BattleStat.SPECIAL_ATTACK, -1, "snarl", "strugglebug");
      this.targetStage(BattleStat.SPECIAL_DEFENSE, -2, "acidspray");
      this.targetStage(BattleStat.ACCURACY, -1, "mudslap");
      this.put(
         new MoveSemantic(
            List.of(new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.ATTACK, 1, 0.1)),
            MoveSemantic.Status.NONE,
            0.0,
            0.0,
            0.0,
            0.0,
            1,
            1,
            false
         ),
         "metalclaw"
      );
      this.heal(0.5, "recover", "roost", "slackoff", "softboiled", "milkdrink", "healorder");
      this.heal(1.0, "rest");
      this.drain(0.5, "gigadrain", "megadrain", "absorb", "drainpunch", "hornleech", "paraboliccharge", "leechlife");
      this.drain(0.75, "drainingkiss");
      this.recoil(0.25, "takedown", "wildcharge");
      this.recoil(0.3333333333333333, "doubleedge", "bravebird", "woodhammer");
      this.recoil(0.5, "headsmash");
      this.recoil(0.5, "lightofruin");
      this.put(new MoveSemantic(List.of(), MoveSemantic.Status.BURN, 0.1, 0.0, 0.0, 0.3333333333333333, 1, 1, false), "flareblitz");
      this.put(new MoveSemantic(List.of(), MoveSemantic.Status.PARALYSIS, 0.1, 0.0, 0.0, 0.33, 1, 1, false), "volttackle");
      this.multi(2, 5, "doubleslap", "furyswipes", "bulletseed", "rockblast", "iciclespear", "pinmissile", "armthrust", "bonerush", "scaleshot");
      this.multi(2, 2, "doublekick", "dualchop", "twineedle");
      this.multi(3, 3, "triplekick", "tripledive");
      this.multi(1, 10, "populationbomb");
      this.protect("protect", "detect", "kingsshield", "spikyshield", "banefulbunker", "obstruct", "silktrap", "burningbulwark");
      this.known
         .put(
            "flamecharge",
            new MoveSemantic(
               List.of(new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.SPEED, 1)), MoveSemantic.Status.NONE, 0.0, 0.0, 0.0, 0.0, 1, 1, false
            )
         );
      this.known
         .put(
            "closecombat",
            new MoveSemantic(
               List.of(
                  new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.DEFENSE, -1),
                  new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.SPECIAL_DEFENSE, -1)
               ),
               MoveSemantic.Status.NONE,
               0.0,
               0.0,
               0.0,
               0.0,
               1,
               1,
               false
            )
         );
      this.known
         .put(
            "superpower",
            new MoveSemantic(
               List.of(
                  new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.ATTACK, -1),
                  new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.DEFENSE, -1)
               ),
               MoveSemantic.Status.NONE,
               0.0,
               0.0,
               0.0,
               0.0,
               1,
               1,
               false
            )
         );
   }

   public MoveSemantic resolveKnown(String moveId) {
      return this.known.getOrDefault(id(moveId), MoveSemantic.plain());
   }

   public MoveSemantic resolve(MoveTemplate move) {
      String id = id(move.getName());
      MoveSemantic semantic = this.known.get(id);
      if (semantic == null) {
         return MoveSemantic.plain();
      } else {
         Double[] chances = move.getEffectChances();
         if (semantic.status() != MoveSemantic.Status.NONE && chances != null && chances.length > 0 && chances[0] != null && semantic.statusChance() < 1.0) {
            double registryChance = Math.max(0.0, Math.min(1.0, chances[0] / 100.0));
            if (registryChance > 0.0) {
               return new MoveSemantic(
                  semantic.stages(),
                  semantic.status(),
                  registryChance,
                  semantic.healFraction(),
                  semantic.drainFraction(),
                  semantic.recoilFraction(),
                  semantic.multiHitMin(),
                  semantic.multiHitMax(),
                  semantic.protect()
               );
            }
         }

         return semantic;
      }
   }

   private void selfStage(BattleStat stat, int stages, String... ids) {
      this.selfStages(List.of(sc(stat, stages)), ids);
   }

   private void selfStages(List<MoveSemantic.StageChange> changes, String... ids) {
      this.put(new MoveSemantic(changes, MoveSemantic.Status.NONE, 0.0, 0.0, 0.0, 0.0, 1, 1, false), ids);
   }

   private void targetStage(BattleStat stat, int stages, String... ids) {
      this.put(
         new MoveSemantic(
            List.of(new MoveSemantic.StageChange(MoveSemantic.Target.TARGET, stat, stages)), MoveSemantic.Status.NONE, 0.0, 0.0, 0.0, 0.0, 1, 1, false
         ),
         ids
      );
   }

   private void targetStageChance(BattleStat stat, int stages, double chance, String... ids) {
      this.put(
         new MoveSemantic(
            List.of(new MoveSemantic.StageChange(MoveSemantic.Target.TARGET, stat, stages, chance)), MoveSemantic.Status.NONE, 0.0, 0.0, 0.0, 0.0, 1, 1, false
         ),
         ids
      );
   }

   private void status(MoveSemantic.Status status, double chance, String... ids) {
      this.put(new MoveSemantic(List.of(), status, chance, 0.0, 0.0, 0.0, 1, 1, false), ids);
   }

   private void heal(double fraction, String... ids) {
      this.put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0.0, fraction, 0.0, 0.0, 1, 1, false), ids);
   }

   private void drain(double fraction, String... ids) {
      this.put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0.0, 0.0, fraction, 0.0, 1, 1, false), ids);
   }

   private void recoil(double fraction, String... ids) {
      this.put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0.0, 0.0, 0.0, fraction, 1, 1, false), ids);
   }

   private void multi(int min, int max, String... ids) {
      this.put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0.0, 0.0, 0.0, 0.0, min, max, false), ids);
   }

   private void protect(String... ids) {
      this.put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0.0, 0.0, 0.0, 0.0, 1, 1, true), ids);
   }

   private void put(MoveSemantic semantic, String... ids) {
      for (String id : ids) {
         this.known.put(id(id), semantic);
      }
   }

   private static MoveSemantic.StageChange sc(BattleStat stat, int amount) {
      return new MoveSemantic.StageChange(MoveSemantic.Target.SELF, stat, amount);
   }

   public static String id(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
   }
}
