package vn.svframe.svframemmo.cobblemon.cosmetic;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-authored cosmetic browser/equip GUI. Left-click equip/toggle; right-click preview. */
public final class CosmeticGui {
    private final CosmeticService cosmetics;
    public CosmeticGui(CosmeticService cosmetics) { this.cosmetics = cosmetics; }

    public void open(ServerPlayerEntity player) {
        SimpleInventory inventory = new SimpleInventory(27);
        Map<Integer, String> bySlot = new HashMap<>();
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int index = 0;
        for (CosmeticDefinition definition : cosmetics.definitions()) {
            if (index >= slots.length) break;
            int slot = slots[index++];
            boolean owned = cosmetics.owned(player.getUuid()).contains(definition.id());
            boolean equipped = definition.id().equals(cosmetics.equipped(player.getUuid()).get(definition.skillId()));
            ItemStack icon = new ItemStack(equipped ? Items.NETHER_STAR : owned ? Items.AMETHYST_SHARD : Items.BARRIER);
            String state = equipped ? " [Equipped]" : owned ? " [Owned]" : " [Locked]";
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(definition.name() + state));
            inventory.setStack(slot, icon);
            bySlot.put(slot, definition.id());
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, owner) -> new Handler(syncId, inv, inventory, bySlot, player.getUuid()),
                Text.literal("SVFrameMMO Cosmetics")));
    }

    private final class Handler extends GenericContainerScreenHandler {
        private final Map<Integer, String> bySlot;
        private final UUID owner;
        Handler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory, Map<Integer, String> bySlot, UUID owner) {
            super(ScreenHandlerType.GENERIC_9X3, syncId, playerInventory, inventory, 3);
            this.bySlot = Map.copyOf(bySlot);
            this.owner = owner;
        }

        @Override public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
            if (!(entity instanceof ServerPlayerEntity player) || !owner.equals(player.getUuid()) || slot < 0 || slot >= 27) return;
            String id = bySlot.get(slot);
            if (id == null) return;
            CosmeticDefinition definition = cosmetics.definition(id);
            if (definition == null) return;
            if (button == 1) {
                CosmeticService.Result result = cosmetics.preview(player, id);
                player.sendMessage(Text.literal(result.success() ? "Preview: " + definition.name() : result.message()), true);
                return;
            }
            String equipped = cosmetics.equipped(player.getUuid()).get(definition.skillId());
            if (definition.id().equals(equipped)) {
                cosmetics.unequip(player, definition.skillId());
                player.sendMessage(Text.literal("Unequipped " + definition.name()), true);
            } else {
                CosmeticService.Result result = cosmetics.equip(player, id);
                player.sendMessage(Text.literal(result.success() ? "Equipped " + definition.name() : result.message()), true);
            }
            player.closeHandledScreen();
            open(player);
        }

        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }
}
