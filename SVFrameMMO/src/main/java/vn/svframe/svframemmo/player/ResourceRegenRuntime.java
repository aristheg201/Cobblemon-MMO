package vn.svframe.svframemmo.player;

import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

/** Applies per-class resource regeneration on the configured tick period. */
public final class ResourceRegenRuntime {
    private long next;

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
                if (regeneration != 0d) resource.regen(data, regeneration * seconds);
            }
        }
    }
}
