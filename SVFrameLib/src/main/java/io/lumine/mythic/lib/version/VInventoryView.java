package io.lumine.mythic.lib.version;
import net.minecraft.entity.player.PlayerInventory; import net.minecraft.inventory.Inventory; import net.minecraft.item.ItemStack; import net.minecraft.screen.ScreenHandlerType; import net.minecraft.server.network.ServerPlayerEntity;
public interface VInventoryView {
    String getTitle(); ScreenHandlerType<?> getType(); Inventory getTopInventory(); PlayerInventory getBottomInventory(); void setCursor(ItemStack stack); ServerPlayerEntity getPlayer(); void close();
}
