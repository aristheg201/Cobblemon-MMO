package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Explicit realtime semantics layered over Cobblemon's dynamic move registry. */
public final class MoveSemanticRegistry {
    private final Map<String, MoveSemantic> known = new HashMap<>();

    public MoveSemanticRegistry() {
        selfStage(BattleStat.ATTACK, 2, "swordsdance");
        selfStage(BattleStat.SPECIAL_ATTACK, 2, "nastyplot");
        selfStages(List.of(sc(BattleStat.SPECIAL_ATTACK, 1), sc(BattleStat.SPECIAL_DEFENSE, 1)), "calmmind");
        selfStages(List.of(sc(BattleStat.ATTACK, 1), sc(BattleStat.SPEED, 1)), "dragondance");
        selfStages(List.of(sc(BattleStat.SPECIAL_ATTACK, 1), sc(BattleStat.SPECIAL_DEFENSE, 1), sc(BattleStat.SPEED, 1)), "quiverdance");
        selfStages(List.of(sc(BattleStat.ATTACK, 1), sc(BattleStat.DEFENSE, 1)), "bulkup");
        selfStages(List.of(sc(BattleStat.ATTACK, 1), sc(BattleStat.SPECIAL_ATTACK, 1)), "workup");
        selfStage(BattleStat.DEFENSE, 2, "irondefense", "acidarmor", "barrier");
        selfStage(BattleStat.DEFENSE, 3, "cottonguard");
        selfStage(BattleStat.DEFENSE, 1, "harden", "defensecurl", "withdraw");
        selfStage(BattleStat.SPECIAL_DEFENSE, 2, "amnesia");
        selfStage(BattleStat.SPEED, 2, "agility", "rockpolish");
        targetStage(BattleStat.DEFENSE, -1, "tailwhip", "leer");
        targetStage(BattleStat.ATTACK, -1, "growl");
        targetStage(BattleStat.SPEED, -2, "scaryface", "stringshot");
        targetStage(BattleStat.ACCURACY, -1, "sandattack", "smokescreen", "kinesis");

        status(MoveSemantic.Status.PARALYSIS, 1.0, "thunderwave", "glare", "stunspore");
        status(MoveSemantic.Status.BURN, 1.0, "willowisp");
        status(MoveSemantic.Status.POISON, 1.0, "poisonpowder");
        status(MoveSemantic.Status.BAD_POISON, 1.0, "toxic");
        status(MoveSemantic.Status.SLEEP, 1.0, "spore", "sleeppowder", "hypnosis", "sing", "lovelykiss");
        status(MoveSemantic.Status.CONFUSION, 1.0, "confuseray", "supersonic", "sweetkiss");

        status(MoveSemantic.Status.PARALYSIS, 0.30, "lick");
        status(MoveSemantic.Status.FLINCH, 0.30, "bite", "headbutt", "rockslide", "ironhead", "zenheadbutt", "airslash");
        status(MoveSemantic.Status.FLINCH, 1.0, "fakeout");
        status(MoveSemantic.Status.BURN, 0.10, "flamethrower", "firepunch", "ember", "heatwave");
        status(MoveSemantic.Status.PARALYSIS, 0.10, "thunderbolt", "thunderpunch", "spark");
        status(MoveSemantic.Status.POISON, 0.30, "sludgebomb", "poisonjab");
        status(MoveSemantic.Status.FREEZE, 0.10, "icebeam", "icepunch", "blizzard");

        heal(0.50, "recover", "roost", "slackoff", "softboiled", "milkdrink", "healorder");
        heal(1.00, "rest");
        drain(0.50, "gigadrain", "megadrain", "absorb", "drainpunch", "drainingkiss", "hornleech", "paraboliccharge");
        recoil(0.25, "takedown", "wildcharge");
        recoil(1.0 / 3.0, "doubleedge", "flareblitz", "bravebird", "woodhammer", "headsmash");
        recoil(0.50, "volttackle", "lightofruin");

        multi(2, 5, "doubleslap", "furyswipes", "bulletseed", "rockblast", "iciclespear", "pinmissile", "armthrust", "bonerush", "scaleshot");
        multi(2, 2, "doublekick", "dualchop", "twineedle");
        multi(3, 3, "triplekick", "tripledive");
        multi(1, 10, "populationbomb");

        protect("protect", "detect", "kingsshield", "spikyshield", "banefulbunker", "obstruct", "silktrap", "burningbulwark");

        known.put("flamecharge", new MoveSemantic(List.of(new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.SPEED, 1)), MoveSemantic.Status.NONE, 0, 0, 0, 0, 1, 1, false));
        known.put("closecombat", new MoveSemantic(List.of(
                new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.DEFENSE, -1),
                new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.SPECIAL_DEFENSE, -1)), MoveSemantic.Status.NONE, 0, 0, 0, 0, 1, 1, false));
        known.put("superpower", new MoveSemantic(List.of(
                new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.ATTACK, -1),
                new MoveSemantic.StageChange(MoveSemantic.Target.SELF, BattleStat.DEFENSE, -1)), MoveSemantic.Status.NONE, 0, 0, 0, 0, 1, 1, false));
    }

    public MoveSemantic resolveKnown(String moveId) { return known.getOrDefault(id(moveId), MoveSemantic.plain()); }

    public MoveSemantic resolve(MoveTemplate move) {
        String id = id(move.getName());
        MoveSemantic semantic = known.get(id);
        if (semantic == null) return MoveSemantic.plain();
        Double[] chances = move.getEffectChances();
        if (semantic.status() != MoveSemantic.Status.NONE && chances != null && chances.length > 0 && chances[0] != null && semantic.statusChance() < 1.0) {
            double registryChance = Math.max(0.0, Math.min(1.0, chances[0] / 100.0));
            if (registryChance > 0) return new MoveSemantic(semantic.stages(), semantic.status(), registryChance,
                    semantic.healFraction(), semantic.drainFraction(), semantic.recoilFraction(), semantic.multiHitMin(), semantic.multiHitMax(), semantic.protect());
        }
        return semantic;
    }

    private void selfStage(BattleStat stat, int stages, String... ids) { selfStages(List.of(sc(stat, stages)), ids); }
    private void selfStages(List<MoveSemantic.StageChange> changes, String... ids) { put(new MoveSemantic(changes, MoveSemantic.Status.NONE, 0, 0, 0, 0, 1, 1, false), ids); }
    private void targetStage(BattleStat stat, int stages, String... ids) { put(new MoveSemantic(List.of(new MoveSemantic.StageChange(MoveSemantic.Target.TARGET, stat, stages)), MoveSemantic.Status.NONE, 0, 0, 0, 0, 1, 1, false), ids); }
    private void status(MoveSemantic.Status status, double chance, String... ids) { put(new MoveSemantic(List.of(), status, chance, 0, 0, 0, 1, 1, false), ids); }
    private void heal(double fraction, String... ids) { put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0, fraction, 0, 0, 1, 1, false), ids); }
    private void drain(double fraction, String... ids) { put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0, 0, fraction, 0, 1, 1, false), ids); }
    private void recoil(double fraction, String... ids) { put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0, 0, 0, fraction, 1, 1, false), ids); }
    private void multi(int min, int max, String... ids) { put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0, 0, 0, 0, min, max, false), ids); }
    private void protect(String... ids) { put(new MoveSemantic(List.of(), MoveSemantic.Status.NONE, 0, 0, 0, 0, 1, 1, true), ids); }
    private void put(MoveSemantic semantic, String... ids) { for (String id : ids) known.put(id(id), semantic); }
    private static MoveSemantic.StageChange sc(BattleStat stat, int amount) { return new MoveSemantic.StageChange(MoveSemantic.Target.SELF, stat, amount); }
    public static String id(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
