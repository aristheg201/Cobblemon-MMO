package vn.svframe.svframecore.player;

/** Tick-driven replacement for the server-plugin task-backed combat state. */
public final class CombatHandler {
    private long lastEntryMillis = System.currentTimeMillis();
    private long lastHitMillis = System.currentTimeMillis();
    private long invulnerableTillMillis;
    private long combatUntilTick;
    private boolean pvpMode;

    public void update(long currentTick, long timeoutTicks) {
        boolean already = isInCombat(currentTick);
        lastHitMillis = System.currentTimeMillis();
        invulnerableTillMillis = 0L;
        if (!already) lastEntryMillis = lastHitMillis;
        combatUntilTick = currentTick + Math.max(1L, timeoutTicks);
    }

    public boolean isInCombat(long currentTick) { return combatUntilTick > currentTick; }
    public void clear() { combatUntilTick = 0L; lastHitMillis = 0L; invulnerableTillMillis = 0L; }
    public long getLastHit() { return lastHitMillis; }
    public long getLastEntry() { return lastEntryMillis; }
    public long getInvulnerableTill() { return invulnerableTillMillis; }
    public boolean isInvulnerable() { return System.currentTimeMillis() < invulnerableTillMillis; }
    public void setInvulnerable(double seconds) { invulnerableTillMillis = System.currentTimeMillis() + (long) (seconds * 1000d); }
    public boolean isInPvpMode() { return pvpMode; }
    public void setPvpMode(boolean pvpMode) { this.pvpMode = pvpMode; }
}
