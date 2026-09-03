package vn.svframe.svframemmo.player;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies per-class resource regeneration on the configured tick period. */
public final class ResourceRegenRuntime {
    private static final long HEALTH_SYNC_GRACE_TICKS = 3L;
    private static final ConcurrentHashMap<UUID, Long> LAST_HEALTH_REGEN = new ConcurrentHashMap<>();

    private long next;

    /**
     * True only around a real positive SVFrameMMO health-regeneration update. The owning-client health packet is sent
     * from ServerPlayerEntity on a later player tick, so keep a short grace window for that packet to be rewritten.
     */
    public static boolean recentlyRegeneratedHealth(UUID playerId, long tick) {
        if (playerId == null) return false;
        Long last = LAST_HEALTH_REGEN.get(playerId);
        if (last == null) return false;
        if (tick >= last && tick - last <= HEALTH_SYNC_GRACE_TICKS) return true;
        if (tick - last > HEALTH_SYNC_GRACE_TICKS) LAST_HEALTH_REGEN.remove(playerId, last);
        return false;
    }

    public void tick(long tick) {
        int period = Math.max(1, SVFrameMMO.config().resourceTickPeriod());
        if (tick < next) return;
        next = tick + period;
        double seconds = period / 20d;
        for (PlayerData data : SVFrameMMO.playerData().online()) {
            var player = data.getPlayer();
            if (player == null || player.isDead()) continue;
            for (PlayerResource resource : PlayerResource.values()) {
                double regeneration = data.getProfess().getHandler(resource).getRegen(data);
                if (regeneration == 0d) continue;

                double beforeHealth = resource == PlayerResource.HEALTH ? data.getHealth() : 0d;
                boolean changed = resource.regen(data, regeneration * seconds);
                if (resource == PlayerResource.HEALTH && changed && data.getHealth() > beforeHealth + 1.0e-6d)
                    LAST_HEALTH_REGEN.put(data.getUniqueId(), tick);
            }
        }
    }
}
