package vn.svframe.svframemmo.cobblemon.move;

import java.util.List;

public record MoveSemantic(
        List<StageChange> stages,
        Status status,
        double statusChance,
        double healFraction,
        double drainFraction,
        double recoilFraction,
        int multiHitMin,
        int multiHitMax,
        boolean protect
) {
    public MoveSemantic {
        stages = stages == null ? List.of() : List.copyOf(stages);
        status = status == null ? Status.NONE : status;
        multiHitMin = Math.max(1, multiHitMin);
        multiHitMax = Math.max(multiHitMin, multiHitMax);
    }

    public static MoveSemantic plain() { return new MoveSemantic(List.of(), Status.NONE, 0, 0, 0, 0, 1, 1, false); }
    public record StageChange(Target target, BattleStat stat, int stages) { }
    public enum Target { SELF, TARGET }
    public enum Status { NONE, PARALYSIS, BURN, POISON, BAD_POISON, SLEEP, FREEZE, CONFUSION, FLINCH }
}
