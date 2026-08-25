package vn.svframe.mythiclibfabric.runtime.skill;
public record ScalingFormula(double base, double perLevel, double min, double max) {
    public ScalingFormula { if (!Double.isFinite(base)||!Double.isFinite(perLevel)||!Double.isFinite(min)||!Double.isFinite(max)||min>max) throw new IllegalArgumentException(); }
    public double evaluate(int level) { double v=base+perLevel*Math.max(0,level-1); return Math.max(min,Math.min(max,v)); }
}
