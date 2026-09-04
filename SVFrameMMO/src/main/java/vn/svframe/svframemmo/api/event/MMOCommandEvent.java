package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Locale;
import java.util.Objects;

/** Cancellable event fired immediately before a player executes a native RPG command root. */
public final class MMOCommandEvent {
    @FunctionalInterface public interface Listener { void onCommand(MMOCommandEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onCommand(event); });

    private final PlayerData data;
    private final String command;
    private boolean cancelled;

    public MMOCommandEvent(PlayerData data, String command) {
        this.data = Objects.requireNonNull(data, "data");
        String normalized = Objects.requireNonNull(command, "command").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("command cannot be blank");
        this.command = normalized;
    }

    public PlayerData getData() { return data; }
    public String getCommand() { return command; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public MMOCommandEvent call() { EVENT.invoker().onCommand(this); return this; }
}
