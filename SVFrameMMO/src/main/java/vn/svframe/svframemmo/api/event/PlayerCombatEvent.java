package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Objects;

/** Fired whenever native combat state transitions between inactive and active. */
public final class PlayerCombatEvent {
    @FunctionalInterface public interface Listener { void onCombat(PlayerCombatEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onCombat(event); });

    private final PlayerData data;
    private final boolean enter;

    public PlayerCombatEvent(PlayerData data, boolean enter) {
        this.data = Objects.requireNonNull(data, "data");
        this.enter = enter;
    }

    public PlayerData getData() { return data; }
    public boolean entersCombat() { return enter; }
    public PlayerCombatEvent call() { EVENT.invoker().onCombat(this); return this; }
}
