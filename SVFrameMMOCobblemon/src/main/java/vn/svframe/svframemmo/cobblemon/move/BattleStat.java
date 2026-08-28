package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;

public enum BattleStat {
    ATTACK(Stats.ATTACK),
    DEFENSE(Stats.DEFENCE),
    SPECIAL_ATTACK(Stats.SPECIAL_ATTACK),
    SPECIAL_DEFENSE(Stats.SPECIAL_DEFENCE),
    SPEED(Stats.SPEED),
    ACCURACY(Stats.ACCURACY),
    EVASION(Stats.EVASION);

    private final Stats cobblemon;
    BattleStat(Stats cobblemon) { this.cobblemon = cobblemon; }
    public Stats cobblemon() { return cobblemon; }

    public static double multiplier(int stage) {
        int clamped = Math.max(-6, Math.min(6, stage));
        if (clamped >= 0) return (2.0 + clamped) / 2.0;
        return 2.0 / (2.0 - clamped);
    }
}
