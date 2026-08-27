package vn.svframe.svframelib.fabric.runtime;

/** Shared public event bus for native Fabric ports of SVFrameLib server-plugin platform events. */
public final class SVFrameLibEventHub {
    private static final EventBus BUS = new EventBus();

    private SVFrameLibEventHub() { }

    public static EventBus events() {
        return BUS;
    }
}
