package vn.svframe.svframemmo.cobblemon.fusion;

public enum FusionTier {
    DANCE(0.10), BASIC(0.25), LEVEL_2(0.50), ADVANCEMENT(0.75), GOD(1.00);
    private final double multiplier;
    FusionTier(double multiplier) { this.multiplier = multiplier; }
    public double multiplier() { return multiplier; }
}
