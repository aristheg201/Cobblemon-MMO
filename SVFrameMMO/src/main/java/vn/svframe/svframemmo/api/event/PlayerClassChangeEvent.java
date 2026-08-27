package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;

/** Cancellable native class transition event. */
public final class PlayerClassChangeEvent {
    public enum Reason { COMMAND_SELECT, COMMAND_FORCE, GUI, PROFILE, UNKNOWN }
    @FunctionalInterface public interface Listener { void onClassChange(PlayerClassChangeEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class, listeners -> event -> {
        for (Listener listener : listeners) listener.onClassChange(event);
    });

    private final PlayerData data;
    private final PlayerClass oldClass;
    private final PlayerClass newClass;
    private final Reason reason;
    private boolean cancelled;

    public PlayerClassChangeEvent(PlayerData data, PlayerClass oldClass, PlayerClass newClass, Reason reason) {
        this.data = data;
        this.oldClass = oldClass;
        this.newClass = newClass;
        this.reason = reason;
    }

    public PlayerData getData() { return data; }
    public net.minecraft.server.network.ServerPlayerEntity getPlayer() { return data.getPlayer(); }
    public PlayerClass getOldClass() { return oldClass; }
    public PlayerClass getNewClass() { return newClass; }
    public Reason getReason() { return reason; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public PlayerClassChangeEvent call() { EVENT.invoker().onClassChange(this); return this; }
}
