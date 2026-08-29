package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Objects;

/** Compatibility event fired after persisted player data is attached to a connecting server player. */
@Deprecated
public final class PlayerDataLoadEvent {
    @FunctionalInterface public interface Listener { void onDataLoad(PlayerDataLoadEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onDataLoad(event); });

    private final PlayerData data;
    public PlayerDataLoadEvent(PlayerData data) { this.data = Objects.requireNonNull(data, "data"); }
    public PlayerData getData() { return data; }
    public PlayerDataLoadEvent call() { EVENT.invoker().onDataLoad(this); return this; }
}
