package vn.svframe.svframemmo.api.event.unlocking;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import vn.svframe.svframemmo.api.player.PlayerData;

public final class ItemUnlockedEvent extends ItemChangeEvent {
    @FunctionalInterface public interface Listener { void onUnlocked(ItemUnlockedEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onUnlocked(event); });
    public ItemUnlockedEvent(PlayerData data, String itemKey) { super(data, itemKey); }
    public ItemUnlockedEvent call() { EVENT.invoker().onUnlocked(this); return this; }
}
