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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Paginated server-authored cosmetic browser. Left-click equip/toggle; right-click preview. */
public final class CosmeticGui {
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int NEXT_SLOT = 53;

    private final CosmeticService cosmetics;

    public CosmeticGui(CosmeticService cosmetics) {
        this.cosmetics = cosmetics;
    }

    public void open(ServerPlayerEntity player) {
        open(player, 0);
    }

    private void open(ServerPlayerEntity player, int requestedPage) {
        List<CosmeticDefinition> definitions = cosmetics.definitions().stream().toList();
        int pages = Math.max(1, (definitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int start = page * PAGE_SIZE;
        int end = Math.min(definitions.size(), start + PAGE_SIZE);

        SimpleInventory inventory = new SimpleInventory(54);
        Map<Integer, String> bySlot = new HashMap<>();
        Map<CosmeticDefinition.Slot, String> equipped = cosmetics.equipped(player.getUuid());
        var owned = cosmetics.owned(player.getUuid());

        for (int index = start; index < end; index++) {
            CosmeticDefinition definition = definitions.get(index);
            int guiSlot = index - start;
            boolean isOwned = owned.contains(definition.id());
            boolean isEquipped = definition.id().equals(equipped.get(definition.slot()));
            ItemStack icon = new ItemStack(isEquipped
                    ? Items.NETHER_STAR
                    : isOwned ? Items.AMETHYST_SHARD : Items.BARRIER);
            String state = isEquipped ? " [Equipped]" : isOwned ? " [Owned]" : " [Locked]";
            icon.set(DataComponentTypes.CUSTOM_NAME, Text.literal(
                    definition.name() + " [" + definition.slot().id() + "]" + state));
            inventory.setStack(guiSlot, icon);
            bySlot.put(guiSlot, definition.id());
        }

        if (page > 0) {
            ItemStack previous = new ItemStack(Items.ARROW);
            previous.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Previous page"));
            inventory.setStack(PREVIOUS_SLOT, previous);
        }
        if (page + 1 < pages) {
            ItemStack next = new ItemStack(Items.ARROW);
            next.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Next page"));
            inventory.setStack(NEXT_SLOT, next);
        }

        int finalPage = page;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, owner) ->
                        new Handler(syncId, inv, inventory, bySlot, player.getUuid(), finalPage, pages),
                Text.literal("SVFrameMMO Cosmetics " + (page + 1) + "/" + pages)));
    }

    private final class Handler extends GenericContainerScreenHandler {
        private final Map<Integer, String> bySlot;
        private final UUID owner;
        private final int page;
        private final int pages;

        Handler(int syncId, PlayerInventory playerInventory, SimpleInventory inventory,
                Map<Integer, String> bySlot, UUID owner, int page, int pages) {
            super(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6);
            this.bySlot = Map.copyOf(bySlot);
            this.owner = owner;
            this.page = page;
            this.pages = pages;
        }

        @Override
        public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity entity) {
            if (!(entity instanceof ServerPlayerEntity player)
                    || !owner.equals(player.getUuid()) || slot < 0 || slot >= 54) return;

            if (slot == PREVIOUS_SLOT && page > 0) {
                player.closeHandledScreen();
                open(player, page - 1);
                return;
            }
            if (slot == NEXT_SLOT && page + 1 < pages) {
                player.closeHandledScreen();
                open(player, page + 1);
                return;
            }

            String id = bySlot.get(slot);
            if (id == null) return;
            CosmeticDefinition definition = cosmetics.definition(id);
            if (definition == null) return;

            if (button == 1) {
                CosmeticService.Result result = cosmetics.preview(player, id);
                player.sendMessage(Text.literal(result.success()
                        ? "Preview: " + definition.name()
                        : result.message()), true);
                return;
            }

            String current = cosmetics.equipped(player.getUuid()).get(definition.slot());
            if (definition.id().equals(current)) {
                cosmetics.unequip(player, definition.slot());
                player.sendMessage(Text.literal("Unequipped " + definition.name()), true);
            } else {
                CosmeticService.Result result = cosmetics.equip(player, id);
                player.sendMessage(Text.literal(result.success()
                        ? "Equipped " + definition.name() + " in " + definition.slot().id()
                        : result.message()), true);
            }

            player.closeHandledScreen();
            open(player, page);
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }
    }
}
