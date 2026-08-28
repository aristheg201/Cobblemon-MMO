package vn.svframe.svframemmo.cobblemon.fusion;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Timestamp-only cooldown storage. No per-player tasks and no idle-player ticking. */
public final class FusionCooldowns {
    private final Map<UUID, Long> potaraUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> danceUntil = new ConcurrentHashMap<>();

    public long potaraRemainingMillis(UUID player) { return remaining(potaraUntil, player); }
    public long danceRemainingMillis(UUID player) { return remaining(danceUntil, player); }
    public boolean potaraReady(UUID player) { return potaraRemainingMillis(player) <= 0L; }
    public boolean danceReady(UUID player) { return danceRemainingMillis(player) <= 0L; }

    public void markPotara(UUID player, int seconds) { mark(potaraUntil, player, seconds); }
    public void markDance(UUID player, int seconds) { mark(danceUntil, player, seconds); }

    private static void mark(Map<UUID, Long> map, UUID player, int seconds) {
        if (player == null) return;
        if (seconds <= 0) { map.remove(player); return; }
        map.put(player, System.currentTimeMillis() + seconds * 1000L);
    }

    private static long remaining(Map<UUID, Long> map, UUID player) {
        if (player == null) return 0L;
        Long until = map.get(player);
        if (until == null) return 0L;
        long left = until - System.currentTimeMillis();
        if (left <= 0L) { map.remove(player, until); return 0L; }
        return left;
    }
}
