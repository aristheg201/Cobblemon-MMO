package vn.svframe.svframelib.api.event;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Fabric-native click event used by MythicLib trigger dispatch. */
public class PlayerClickEvent {
    @FunctionalInterface
    public interface Listener {
        void onPlayerClick(PlayerClickEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onPlayerClick(event);
            });

    private final ServerPlayerEntity player;
    private final EquipmentSlot hand;
    private final boolean leftClick;
    private final BlockPos clickedBlock;
    private final ItemStack item;
    private boolean cancelled;

    public PlayerClickEvent(ServerPlayerEntity player, EquipmentSlot hand, boolean leftClick,
                            BlockPos clickedBlock, ItemStack item) {
        this.player = player;
        this.hand = hand;
        this.leftClick = leftClick;
        this.clickedBlock = clickedBlock;
        this.item = item;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public boolean hasBlock() {
        return clickedBlock != null;
    }

    public BlockPos getClickedBlock() {
        return clickedBlock;
    }

    public boolean hasItem() {
        return item != null;
    }

    public ItemStack getItem() {
        return item;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public EquipmentSlot getHand() {
        return hand;
    }

    public boolean isLeftClick() {
        return leftClick;
    }

    public PlayerClickEvent call() {
        EVENT.invoker().onPlayerClick(this);
        return this;
    }
}
