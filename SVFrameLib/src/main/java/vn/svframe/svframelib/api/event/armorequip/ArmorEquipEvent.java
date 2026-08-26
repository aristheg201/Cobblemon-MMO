package vn.svframe.svframelib.api.event.armorequip;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric-native cancellable armor equipment event. */
public final class ArmorEquipEvent {
    public enum EquipMethod {
        SHIFT_CLICK,
        DRAG,
        PICK_DROP,
        HOTBAR,
        HOTBAR_SWAP,
        DISPENSER,
        BROKE,
        DEATH
    }

    @FunctionalInterface
    public interface Listener {
        void onArmorEquip(ArmorEquipEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onArmorEquip(event);
            });

    private final ServerPlayerEntity player;
    private boolean cancel;
    private final EquipMethod equipType;
    private final ArmorType type;
    private ItemStack oldArmorPiece;
    private ItemStack newArmorPiece;

    public ArmorEquipEvent(ServerPlayerEntity player, EquipMethod equipType, ArmorType type,
                           ItemStack oldArmorPiece, ItemStack newArmorPiece) {
        this.player = player;
        this.equipType = equipType;
        this.type = type;
        this.oldArmorPiece = oldArmorPiece;
        this.newArmorPiece = newArmorPiece;
    }

    public ServerPlayerEntity getPlayer() {
        return player;
    }

    public void setCancelled(boolean cancelled) {
        this.cancel = cancelled;
    }

    public boolean isCancelled() {
        return cancel;
    }

    public ArmorType getType() {
        return type;
    }

    public ItemStack getOldArmorPiece() {
        return oldArmorPiece;
    }

    public void setOldArmorPiece(ItemStack oldArmorPiece) {
        this.oldArmorPiece = oldArmorPiece;
    }

    public ItemStack getNewArmorPiece() {
        return newArmorPiece;
    }

    public void setNewArmorPiece(ItemStack newArmorPiece) {
        this.newArmorPiece = newArmorPiece;
    }

    public EquipMethod getMethod() {
        return equipType;
    }

    public ArmorEquipEvent call() {
        EVENT.invoker().onArmorEquip(this);
        return this;
    }
}
