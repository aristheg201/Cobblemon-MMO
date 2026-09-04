package vn.svframe.svframemmo.skill.cast;

/** Vanilla player inputs exposed by the native MMOCore-compatible casting API. */
public enum PlayerKey {
    LEFT_CLICK(false),
    RIGHT_CLICK(false),
    DROP(true),
    SWAP_HANDS(true),
    CROUCH(false);

    private final boolean cancellableEvent;

    PlayerKey(boolean cancellableEvent) { this.cancellableEvent = cancellableEvent; }

    public boolean shouldCancelEvent() { return cancellableEvent; }
}
