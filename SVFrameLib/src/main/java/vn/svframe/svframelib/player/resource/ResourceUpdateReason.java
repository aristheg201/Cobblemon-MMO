package vn.svframe.svframelib.player.resource;

public enum ResourceUpdateReason {
    REGENERATION,
    SKILL,
    WAYPOINT,
    CHOOSE_CLASS(false),
    ITEM,
    MECHANIC,
    CLAMPING(false),
    COMMAND,
    OTHER;

    private final boolean callEvent;

    ResourceUpdateReason() { this(true); }
    ResourceUpdateReason(boolean callEvent) { this.callEvent = callEvent; }

    public boolean callsEvent() { return callEvent; }
    public boolean isRegeneration() { return this == REGENERATION || this == SKILL; }
}
