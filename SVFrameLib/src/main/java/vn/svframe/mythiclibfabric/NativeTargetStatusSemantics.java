package vn.svframe.mythiclibfabric;

final class NativeTargetStatusSemantics {
    private NativeTargetStatusSemantics() { }

    static int durationTicks(double seconds) {
        return (int) (seconds * 20.0d);
    }

    static int amplifier(double value) {
        return (int) value;
    }

    static int burnFireTicks(int currentFireTicks, double durationSeconds) {
        return (int) (Math.max(0, currentFireTicks) + durationSeconds * 20.0d);
    }
}
