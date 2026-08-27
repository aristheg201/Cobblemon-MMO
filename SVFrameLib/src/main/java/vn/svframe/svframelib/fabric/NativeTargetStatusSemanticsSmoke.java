package vn.svframe.svframelib.fabric;

/** Regression smoke for the numeric semantics used by SVFrameLib 1.7.1 target/status handlers. */
public final class NativeTargetStatusSemanticsSmoke {
    private NativeTargetStatusSemanticsSmoke() { }

    public static void main(String[] args) {
        require(NativeTargetStatusSemantics.durationTicks(1.75d) == 35, "duration truncation");
        require(NativeTargetStatusSemantics.durationTicks(0.049d) == 0, "zero-tick duration remains zero");
        require(NativeTargetStatusSemantics.amplifier(1.9d) == 1, "amplifier truncation");
        require(NativeTargetStatusSemantics.amplifier(-1.9d) == -1, "negative amplifier truncation");
        require(NativeTargetStatusSemantics.burnFireTicks(-12, 2.5d) == 50, "burn clamps negative current ticks");
        require(NativeTargetStatusSemantics.burnFireTicks(17, 2.5d) == 67, "burn stacks current fire ticks");
        System.out.println("TARGET_STATUS_SEMANTICS=PASS");
    }

    private static void require(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
