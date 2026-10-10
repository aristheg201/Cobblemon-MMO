package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Objects;

/** Fired before a non-forced skill casting session is closed. */
public final class PlayerExitCastingModeEvent {
    @FunctionalInterface public interface Listener { void onExitCasting(PlayerExitCastingModeEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onExitCasting(event);
    });

    private final PlayerData data;
    private boolean cancelled;

    public PlayerExitCastingModeEvent(PlayerData data) { this.data = Objects.requireNonNull(data, "data"); }
    public PlayerData getData() { return data; }
    public ServerPlayerEntity getPlayer() { return data.getPlayer(); }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public PlayerExitCastingModeEvent call() { EVENT.invoker().onExitCasting(this); return this; }
}
