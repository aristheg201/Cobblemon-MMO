package vn.svframe.svframemmo.cobblemon.move;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-driven paged catalog UI. Buying only unlocks persistent SVFrameMMO ownership. */
public final class PokemonSkillShopGui {
    public static final int PAGE_SIZE = 45;
    private static final int INVENTORY_SIZE = 54;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final ServerPlayerEntity player;
    private final PokemonSkillShopService shop;
    private final int page;
    private final Map<Integer, PokemonSkillShopService.Offer> offerBySlot = new LinkedHashMap<>();

    public PokemonSkillShopGui(ServerPlayerEntity player, PokemonSkillShopService shop, int page) {
        this.player = player;
        this.shop = shop;
        this.page = Math.max(0, page);
    }

    public void open() {
        List<PokemonSkillShopService.Offer> offers = shop.offers(player.getUuid());
        int pageCount = Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int actualPage = Math.min(page, pageCount - 1);
        int start = actualPage * PAGE_SIZE;
        int end = Math.min(offers.size(), start + PAGE_SIZE);

        SimpleInventory inventory = new SimpleInventory(INVENTORY_SIZE);
        offerBySlot.clear();
        for (int index = start; index < end; index++) {
            int slot = index - start;
            PokemonSkillShopService.Offer offer = offers.get(index);
            offerBySlot.put(slot, offer);
            inventory.setStack(slot, displayOffer(offer));
        }

        if (actualPage > 0) inventory.setStack(PREVIOUS_SLOT, named(new ItemStack(Items.ARROW), "Previous page"));
        if (actualPage + 1 < pageCount) inventory.setStack(NEXT_SLOT, named(new ItemStack(Items.ARROW), "Next page"));
        inventory.setStack(INFO_SLOT, named(new ItemStack(Items.BOOK),
                "Page " + (actualPage + 1) + "/" + pageCount + " | Currency: " + shop.currencyLabel() + " | Owned skills stay in SVFrameMMO"));

        String title = shop.title() + " " + (actualPage + 1) + "/" + pageCount;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInventory, ignoredPlayer) ->
                new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInventory, inventory, 6) {
                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity clickingPlayer) {
                        if (slotIndex >= 0 && slotIndex < INVENTORY_SIZE) {
                            if (actionType == SlotActionType.PICKUP) handle(slotIndex, actualPage);
                            return;
                        }
                        if (actionType == SlotActionType.QUICK_MOVE) return;
                        super.onSlotClick(slotIndex, button, actionType, clickingPlayer);
                    }

                    @Override
                    public ItemStack quickMove(PlayerEntity player, int slot) {
                        return ItemStack.EMPTY;
                    }
                }, Text.literal(title)));
    }

    private ItemStack displayOffer(PokemonSkillShopService.Offer offer) {
        ItemStack stack = PokemonSkillIconResolver.stack(offer.moveId());
        String status = offer.owned()
                ? "Owned | manage/bind/upgrade in /mmo skills"
                : "Buy: " + PokemonSkillShopService.format(offer.price()) + " " + shop.currencyLabel();
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(offer.name() + " | " + status));
        return stack;
    }

    private void handle(int slot, int actualPage) {
        if (slot == PREVIOUS_SLOT && actualPage > 0) {
            shop.open(player, actualPage - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            shop.open(player, actualPage + 1);
            return;
        }
        PokemonSkillShopService.Offer offer = offerBySlot.get(slot);
        if (offer == null) return;
        if (offer.owned()) {
            player.sendMessage(Text.literal(offer.name() + " is already owned. Use /mmo skills to manage its binding and level."), true);
            return;
        }
        shop.purchase(player, offer.moveId(), actualPage);
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        return stack;
    }
}
