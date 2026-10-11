package vn.svframe.svframemmo.cobblemon.move;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class PlayerMoveCoverage {
   private static final Map<String, PlayerMoveCoverage.Family> ADAPTERS;

   private PlayerMoveCoverage() {
   }

   private static void add(Map<String, PlayerMoveCoverage.Family> result, PlayerMoveCoverage.Family family, String... ids) {
      for (String id : ids) {
         if (result.putIfAbsent(id, family) != null) {
            throw new IllegalStateException("Duplicate player move: " + id);
         }
      }
   }

   public static PlayerMoveCoverage.Family family(String move) {
      return ADAPTERS.get(MoveSemanticRegistry.id(move));
   }

   public static boolean purchasable(String move) {
      return family(move) != null;
   }

   public static Set<String> purchaseAdapters() {
      return ADAPTERS.keySet();
   }

   public static String reason(String move) {
      PlayerMoveCoverage.Family family = family(move);
      return family == null ? "requires-move-specific-adapter-and-runtime-validation" : "native-overworld-" + family.name().toLowerCase(Locale.ROOT);
   }

   public static String effectKey(String move) {
      String id = MoveSemanticRegistry.id(move);
      PlayerMoveCoverage.Family family = family(id);
      return family == null
         ? ""
         : "svframemmo_cobblemon.effect." + (family == PlayerMoveCoverage.Family.SIGNATURE ? id : family.name().toLowerCase(Locale.ROOT));
   }

   static {
      TreeMap<String, PlayerMoveCoverage.Family> moves = new TreeMap<>();
      add(moves, PlayerMoveCoverage.Family.SIGNATURE, "bite", "lick");
      add(
         moves,
         PlayerMoveCoverage.Family.STRIKE,
         "tackle",
         "pound",
         "scratch",
         "cut",
         "strength",
         "slam",
         "megapunch",
         "megakick",
         "visegrip",
         "wingattack",
         "watergun",
         "hydropump",
         "vinewhip",
         "leafage",
         "seedbomb",
         "rockthrow",
         "powergem",
         "aurasphere",
         "shadowpunch",
         "dragonpulse",
         "dragonclaw",
         "fairywind",
         "gust",
         "aerialace",
         "magicalleaf",
         "swift"
      );
      add(
         moves,
         PlayerMoveCoverage.Family.SECONDARY,
         "ember",
         "flamethrower",
         "firepunch",
         "fireblast",
         "scald",
         "waterpulse",
         "waterfall",
         "thundershock",
         "thunderbolt",
         "thunderpunch",
         "spark",
         "thunder",
         "icebeam",
         "icepunch",
         "powdersnow",
         "poisonsting",
         "sludge",
         "sludgebomb",
         "poisonjab",
         "confusion",
         "psybeam",
         "psychic",
         "extrasensory",
         "darkpulse",
         "shadowball",
         "dragonbreath",
         "ironhead",
         "flashcannon",
         "metalclaw",
         "moonblast",
         "bugbuzz",
         "mudslap",
         "acidspray",
         "flamecharge",
         "closecombat",
         "superpower"
      );
      add(
         moves,
         PlayerMoveCoverage.Family.AREA,
         "surf",
         "earthquake",
         "dazzlinggleam",
         "heatwave",
         "discharge",
         "blizzard",
         "rockslide",
         "snarl",
         "strugglebug",
         "acid"
      );
      add(
         moves,
         PlayerMoveCoverage.Family.DASH,
         "quickattack",
         "extremespeed",
         "aquajet",
         "bulletpunch",
         "iceshard",
         "shadowsneak",
         "machpunch",
         "vacuumwave",
         "accelerock"
      );
      add(moves, PlayerMoveCoverage.Family.HEAL, "recover", "slackoff", "softboiled", "milkdrink", "healorder", "roost");
      add(moves, PlayerMoveCoverage.Family.DRAIN, "absorb", "megadrain", "gigadrain", "drainpunch", "drainingkiss", "hornleech", "leechlife");
      add(moves, PlayerMoveCoverage.Family.RECOIL, "takedown", "wildcharge", "doubleedge", "flareblitz", "bravebird", "woodhammer", "headsmash");
      add(
         moves,
         PlayerMoveCoverage.Family.MULTI_HIT,
         "doubleslap",
         "furyswipes",
         "bulletseed",
         "rockblast",
         "iciclespear",
         "pinmissile",
         "armthrust",
         "bonerush",
         "doublekick",
         "dualchop"
      );
      add(
         moves,
         PlayerMoveCoverage.Family.BUFF,
         "swordsdance",
         "nastyplot",
         "calmmind",
         "dragondance",
         "quiverdance",
         "bulkup",
         "workup",
         "irondefense",
         "acidarmor",
         "barrier",
         "cottonguard",
         "harden",
         "defensecurl",
         "withdraw",
         "amnesia",
         "agility",
         "rockpolish"
      );
      add(moves, PlayerMoveCoverage.Family.DEBUFF, "tailwhip", "leer", "growl", "scaryface", "stringshot", "sandattack", "smokescreen", "kinesis");
      add(
         moves,
         PlayerMoveCoverage.Family.STATUS,
         "thunderwave",
         "glare",
         "stunspore",
         "willowisp",
         "poisonpowder",
         "toxic",
         "spore",
         "sleeppowder",
         "hypnosis",
         "sing",
         "lovelykiss",
         "confuseray",
         "supersonic",
         "sweetkiss"
      );
      add(moves, PlayerMoveCoverage.Family.GUARD, "protect", "detect");
      add(moves, PlayerMoveCoverage.Family.TELEPORT, "teleport");
      ADAPTERS = Collections.unmodifiableMap(moves);
   }

   public static enum Family {
      SIGNATURE,
      STRIKE,
      SECONDARY,
      AREA,
      DASH,
      HEAL,
      DRAIN,
      RECOIL,
      MULTI_HIT,
      BUFF,
      DEBUFF,
      STATUS,
      GUARD,
      TELEPORT;
   }
}
