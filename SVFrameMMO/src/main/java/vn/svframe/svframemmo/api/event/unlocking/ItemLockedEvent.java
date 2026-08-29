package vn.svframe.svframemmo.api.event.unlocking;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;

public final class ItemLockedEvent extends ItemChangeEvent {
    @FunctionalInterface public interface Listener { void onLocked(ItemLockedEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onLocked(event); });
    public ItemLockedEvent(PlayerData data, String itemKey) { super(data, itemKey); }
    public ItemLockedEvent call() { EVENT.invoker().onLocked(this); return this; }
}
