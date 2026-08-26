package vn.svframe.svframelib.profile;

public enum ProfileSessionState {
    CREATED,
    OPENING,
    OPEN,
    CLOSING,
    ABORTING,
    DEAD,
    DEAD_EARLY;

    public boolean wasReady() {
        return this == OPEN || this == CLOSING || this == DEAD;
    }

    public boolean isClosing() {
        return this == CLOSING || this == ABORTING;
    }

    public boolean isWaiting() {
        return this == CLOSING || this == ABORTING || this == OPENING;
    }

    public boolean isDead() {
        return this == DEAD || this == DEAD_EARLY;
    }
}
