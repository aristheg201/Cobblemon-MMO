package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skill.cast.PlayerKey;

import java.util.Objects;

/** Cancellable native wrapper for the five MMOCore casting inputs. */
public final class PlayerKeyPressEvent {
    @FunctionalInterface public interface Listener { void onKeyPress(PlayerKeyPressEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onKeyPress(event);
    });

    private final PlayerData data;
    private final PlayerKey pressed;
    private boolean cancelled;

    public PlayerKeyPressEvent(PlayerData data, PlayerKey pressed) {
        this.data = Objects.requireNonNull(data, "data");
        this.pressed = Objects.requireNonNull(pressed, "pressed");
    }

    public PlayerData getData() { return data; }
    public ServerPlayerEntity getPlayer() { return data.getPlayer(); }
    public PlayerKey getPressed() { return pressed; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public PlayerKeyPressEvent call() { EVENT.invoker().onKeyPress(this); return this; }
}
