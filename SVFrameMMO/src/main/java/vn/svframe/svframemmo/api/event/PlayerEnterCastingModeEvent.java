package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Objects;

/** Fired before a player enters the configured skill casting mode. */
public final class PlayerEnterCastingModeEvent {
    @FunctionalInterface public interface Listener { void onEnterCasting(PlayerEnterCastingModeEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onEnterCasting(event);
    });

    private final PlayerData data;
    private boolean cancelled;

    public PlayerEnterCastingModeEvent(PlayerData data) { this.data = Objects.requireNonNull(data, "data"); }
    public PlayerData getData() { return data; }
    public ServerPlayerEntity getPlayer() { return data.getPlayer(); }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public PlayerEnterCastingModeEvent call() { EVENT.invoker().onEnterCasting(this); return this; }
}
