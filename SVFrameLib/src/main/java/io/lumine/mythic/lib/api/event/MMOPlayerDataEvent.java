package io.lumine.mythic.lib.api.event;

import io.lumine.mythic.lib.api.player.MMOPlayerData;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Fabric-native equivalent of MythicLib 1.7.1's Bukkit MMOPlayerDataEvent.
 * The player reference is captured when the event is constructed, matching
 * Bukkit PlayerEvent semantics instead of resolving MMOPlayerData lazily.
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

    /** Native Fabric equivalent of Bukkit PlayerEvent#getPlayer(). */
    public ServerPlayerEntity getPlayer() {
        return player;
    }
}
