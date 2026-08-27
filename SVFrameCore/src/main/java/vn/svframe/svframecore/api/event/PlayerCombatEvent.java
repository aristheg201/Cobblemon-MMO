package vn.svframe.svframecore.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframecore.api.player.PlayerData;

import java.util.Objects;

/** Fabric-native combat state transition event. */
public final class PlayerCombatEvent {
    @FunctionalInterface
    public interface Listener { void onCombatChange(PlayerCombatEvent event); }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onCombatChange(event);
            });

    private final PlayerData data;
    private final boolean entersCombat;

    public PlayerCombatEvent(PlayerData data, boolean entersCombat) {
        this.data = Objects.requireNonNull(data, "Player data cannot be null");
        this.entersCombat = entersCombat;
    }

    public PlayerCombatEvent call() { EVENT.invoker().onCombatChange(this); return this; }
    public PlayerData getData() { return data; }
    public boolean entersCombat() { return entersCombat; }
}
