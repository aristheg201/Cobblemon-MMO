package vn.svframe.svframemmo.runtime;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

/** Rebinds persistent MMO state when Minecraft replaces the player entity during respawn/clone. */
public final class PlayerRespawnRuntime implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            float previousHealth = oldPlayer.getHealth();
            SVFrameMMO.playerData().join(newPlayer);

            // Try immediately in case SVFrameLib already rebound its attribute handlers.
            restoreHealth(newPlayer, alive, previousHealth);

            // Also queue once after every AFTER_RESPAWN listener has run. This makes the result independent
            // of mod listener registration order and guarantees MAX_HEALTH is the real rebound value.
            var server = newPlayer.getServer();
            if (server != null) server.execute(() -> restoreHealth(newPlayer, alive, previousHealth));
        });
    }

    private static void restoreHealth(ServerPlayerEntity player, boolean alive, float previousHealth) {
        if (player == null || player.isDisconnected()) return;
        PlayerData data = SVFrameMMO.playerData().get(player);
        double max = data.getMaxResource(PlayerResource.HEALTH);
        double target = alive ? Math.min(Math.max(0.0d, previousHealth), max) : max;
        data.setResource(PlayerResource.HEALTH, target, ResourceUpdateReason.CHOOSE_CLASS);
    }
}
