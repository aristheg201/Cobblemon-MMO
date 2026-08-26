package vn.svframe.svframelib.player.cooldown;

/** Millisecond-backed cooldown record; public API exposes durations in seconds. */
public class CooldownInfo {
    private final long initialCooldown;
    private final long castTime;
    private long nextUse;

    public CooldownInfo(double cooldown) {
        castTime = System.currentTimeMillis();
        initialCooldown = (long) (cooldown * 1000d);
        nextUse = castTime + initialCooldown;
    }

    public long getCastTime() { return castTime; }
    public long getInitialCooldown() { return initialCooldown; }
    public long getNextUse() { return nextUse; }
    public long getRemaining() { return Math.max(0L, nextUse - System.currentTimeMillis()); }
    public boolean hasEnded() { return System.currentTimeMillis() > nextUse; }

    public void reduceRemainingCooldown(double p) {
        if (p < 0d || p > 1d) throw new IllegalArgumentException("p must be between 0 and 1");
        nextUse -= (long) (getRemaining() * p);
    }

    public void reduceInitialCooldown(double p) {
        if (p < 0d || p > 1d) throw new IllegalArgumentException("p must be between 0 and 1");
        nextUse -= (long) (initialCooldown * p);
    }

    public void reduceFlat(double seconds) {
        nextUse -= (long) (1000d * seconds);
    }
}
