package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
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
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Server-driven paged catalog UI. Buying only unlocks persistent SVFrameMMO ownership. */
public final class PokemonSkillShopGui {
    public static final int PAGE_SIZE = 36;
    private static final int INVENTORY_SIZE = 54;
    private static final int FIRST_OFFER_SLOT = 9;
    private static final int PREVIOUS_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int CLOSE_SLOT = 50;
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

        ItemStack filler = named(new ItemStack(Items.BLACK_STAINED_GLASS_PANE), " ");
        for (int slot = 0; slot < 9; slot++) inventory.setStack(slot, filler.copy());
        for (int slot = 45; slot < 54; slot++) inventory.setStack(slot, filler.copy());

        ItemStack header = named(new ItemStack(Items.NETHER_STAR), "KỸ NĂNG POKÉMON");
        header.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Chọn Skill cần học ở 4 hàng bên dưới.").formatted(Formatting.GRAY),
                Text.literal("Xanh lá = đã sở hữu • Vàng = có thể mua").formatted(Formatting.DARK_GRAY),
                Text.literal("Tiền tệ: " + shop.currencyLabel()).formatted(Formatting.GOLD)
        )));
        inventory.setStack(4, header);

        for (int index = start; index < end; index++) {
            int slot = FIRST_OFFER_SLOT + (index - start);
            PokemonSkillShopService.Offer offer = offers.get(index);
            offerBySlot.put(slot, offer);
            inventory.setStack(slot, displayOffer(offer));
        }

        if (actualPage > 0) inventory.setStack(PREVIOUS_SLOT, nav(Items.ARROW, "← TRANG TRƯỚC", "Trang " + actualPage + "/" + pageCount));
        if (actualPage + 1 < pageCount) inventory.setStack(NEXT_SLOT, nav(Items.ARROW, "TRANG SAU →", "Trang " + (actualPage + 2) + "/" + pageCount));

        ItemStack info = named(new ItemStack(Items.BOOK), "TRANG " + (actualPage + 1) + "/" + pageCount);
        info.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Tổng Skill: " + offers.size()).formatted(Formatting.GRAY),
                Text.literal("Tiền tệ: " + shop.currencyLabel()).formatted(Formatting.GOLD),
                Text.literal("Skill đã mua được lưu vĩnh viễn trong SVFrameMMO.").formatted(Formatting.DARK_GRAY)
        )));
        inventory.setStack(INFO_SLOT, info);

        ItemStack close = named(new ItemStack(Items.BARRIER), "ĐÓNG");
        close.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Nhấp để đóng cửa hàng.").formatted(Formatting.GRAY)
        )));
        inventory.setStack(CLOSE_SLOT, close);

        String title = "Kỹ năng Pokémon • " + (actualPage + 1) + "/" + pageCount;
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
        stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(offer.name()).formatted(
                offer.owned() ? Formatting.GREEN : Formatting.LIGHT_PURPLE));

        ArrayList<Text> lore = new ArrayList<>();
        MoveTemplate move = Moves.getByName(offer.moveId());
        if (move != null) {
            CobblemonMoveProfile profile = CobblemonMoveProfile.of(move);
            lore.add(Text.literal("Hệ: ").formatted(Formatting.GRAY)
                    .append(Text.literal(move.getElementalType().getName()).formatted(Formatting.AQUA))
                    .append(Text.literal("  •  Loại: ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(move.getDamageCategory().getName()).formatted(Formatting.WHITE)));
            if (profile.baseDamage() > 0d)
                lore.add(Text.literal("Sát thương gốc: ").formatted(Formatting.GRAY)
                        .append(Text.literal(formatNumber(profile.baseDamage())).formatted(Formatting.RED)));
            if (profile.healBase() > 0d)
                lore.add(Text.literal("Hồi phục gốc: ").formatted(Formatting.GRAY)
                        .append(Text.literal(formatNumber(profile.healBase())).formatted(Formatting.GREEN)));
            lore.add(Text.literal("Hồi chiêu gốc: ").formatted(Formatting.GRAY)
                    .append(Text.literal(formatNumber(profile.cooldownSeconds()) + "s").formatted(Formatting.GOLD)));
        }

        lore.add(Text.empty());
        if (offer.owned()) {
            lore.add(Text.literal("✔ ĐÃ SỞ HỮU").formatted(Formatting.GREEN));
            lore.add(Text.literal("Quản lý / gắn / nâng Skill trong menu Kỹ năng.").formatted(Formatting.GRAY));
        } else {
            lore.add(Text.literal("Giá: ").formatted(Formatting.GRAY)
                    .append(Text.literal(PokemonSkillShopService.format(offer.price()) + " " + shop.currencyLabel())
                            .formatted(Formatting.GOLD)));
            lore.add(Text.literal("► Nhấp để mua Skill").formatted(Formatting.YELLOW));
        }
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
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
        if (slot == CLOSE_SLOT) {
            player.closeHandledScreen();
            return;
        }
        PokemonSkillShopService.Offer offer = offerBySlot.get(slot);
        if (offer == null) return;
        if (offer.owned()) {
            player.sendMessage(Text.literal("Bạn đã sở hữu " + offer.name() + ". Hãy mở menu Kỹ năng để gắn hoặc nâng cấp."), true);
            return;
        }
        shop.purchase(player, offer.moveId(), actualPage);
    }

    private static ItemStack nav(net.minecraft.item.Item item, String name, String lore) {
        ItemStack stack = named(new ItemStack(item), name);
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(Text.literal(lore).formatted(Formatting.GRAY))));
        return stack;
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        stack.set(DataComponentTypes.HIDE_ADDITIONAL_TOOLTIP, Unit.INSTANCE);
        return stack;
    }

    private static String formatNumber(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
