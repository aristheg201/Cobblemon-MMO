package vn.svframe.svframemmo.experience;

import java.util.Objects;
import java.util.UUID;

/** Native timed experience booster. extra=1 means +100%. */
public final class Booster {
    private final UUID id = UUID.randomUUID();
    private final long createdAt = System.currentTimeMillis();
    private final String targetKey;
    private final double extra;
    private final String author;
    private long durationMillis;

    public Booster(String author, String targetKey, double extra, long durationSeconds) {
        if (durationSeconds < 0) throw new IllegalArgumentException("durationSeconds");
        this.author = author;
        this.targetKey = targetKey;
        this.extra = extra;
        this.durationMillis = durationSeconds * 1000L;
    }

    public UUID getUniqueId() { return id; }
    public String getTargetKey() { return targetKey; }
    public double getExtra() { return extra; }
    public String getAuthor() { return author; }
    public long getCreationDate() { return createdAt; }
    public long getDuration() { return durationMillis; }
    public void addDuration(long millis) { durationMillis += Math.max(0, millis); }
    public long getLeft() { return Math.max(0L, createdAt + durationMillis - System.currentTimeMillis()); }
    public boolean isTimedOut() { return getLeft() == 0L; }
    public boolean canStackWith(Booster other) { return Double.compare(extra, other.extra) == 0 && Objects.equals(targetKey, other.targetKey); }
}
