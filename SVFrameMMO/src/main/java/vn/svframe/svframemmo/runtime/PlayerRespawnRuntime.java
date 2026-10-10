package vn.svframe.svframemmo.runtime;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import vn.svframe.svframemmo.SVFrameMMO;

/** Rebinds persistent MMO state when Minecraft replaces the player entity during respawn/clone. */
public final class PlayerRespawnRuntime implements ModInitializer {
    @Override
    public void onInitialize() {
        // PlayerDataManager.join() owns the whole transfer: it snapshots/detaches the old entity first,
        // then attaches the replacement. Death health (0) becomes a full refill in PlayerData.attach(),
        // while alive clones preserve the old entity's live health. No delayed second write is needed.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                SVFrameMMO.playerData().join(newPlayer));
    }
}
