package vn.svframe.svframemmo.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import vn.svframe.svframemmo.api.player.PlayerData;

import java.util.Objects;

/** Cancellable custom-fishing event whose caught stack can be replaced before rewards are finalized. */
public final class CustomPlayerFishEvent {
    @FunctionalInterface public interface Listener { void onCustomFish(CustomPlayerFishEvent event); }
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> { for (Listener listener : listeners) listener.onCustomFish(event); });

    private final PlayerData data;
    private final ItemEntity droppedItem;
    private boolean cancelled;

    public CustomPlayerFishEvent(PlayerData data, ItemEntity droppedItem) {
        this.data = Objects.requireNonNull(data, "data");
        this.droppedItem = Objects.requireNonNull(droppedItem, "droppedItem");
    }

    public PlayerData getData() { return data; }
    public ItemEntity getDroppedItem() { return droppedItem; }
    public ItemStack getCaught() { return droppedItem.getStack(); }
    public void setCaught(ItemStack caught) { droppedItem.setStack(Objects.requireNonNull(caught, "caught").copy()); }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public CustomPlayerFishEvent call() { EVENT.invoker().onCustomFish(this); return this; }
}
