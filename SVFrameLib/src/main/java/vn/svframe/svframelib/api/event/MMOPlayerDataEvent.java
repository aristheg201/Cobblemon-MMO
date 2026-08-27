package vn.svframe.svframelib.api.event;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Fabric-native equivalent of SVFrameLib 1.7.1's server-plugin platform MMOPlayerDataEvent.
 * The player reference is captured when the event is constructed, matching
 * server-plugin platform PlayerEvent semantics instead of resolving MMOPlayerData lazily.
 */
public abstract class MMOPlayerDataEvent {
    private final MMOPlayerData playerData;
    private final ServerPlayerEntity player;

    protected MMOPlayerDataEvent(MMOPlayerData playerData) {
        this.player = playerData.getPlayer();
        this.playerData = playerData;
    }

    public MMOPlayerData getData() {
        return playerData;
    }

    /** Native Fabric equivalent of server-plugin platform PlayerEvent#getPlayer(). */
    public ServerPlayerEntity getPlayer() {
        return player;
    }
}
