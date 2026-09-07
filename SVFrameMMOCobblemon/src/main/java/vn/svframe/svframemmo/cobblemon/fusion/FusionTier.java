package vn.svframe.svframemmo.cobblemon.fusion;

public enum FusionTier {
    DANCE(0.05), BASIC(0.10), LEVEL_2(0.15), ADVANCEMENT(0.20), GOD(0.25);
    private final double multiplier;
    FusionTier(double multiplier) { this.multiplier = multiplier; }
    public double multiplier() { return multiplier; }
}
