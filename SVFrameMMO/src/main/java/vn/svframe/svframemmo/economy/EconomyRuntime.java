package vn.svframe.svframemmo.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.DynamicOps;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import vn.svframe.svframelib.api.economy.CurrencyKey;
import vn.svframe.svframelib.api.economy.CurrencyService;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.gui.PluginInventory;
import vn.svframe.svframemmo.config.DefaultFiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native currency deposit and gold-pouch player surface backed by SVFrameLib CurrencyService. */
public final class EconomyRuntime {
    private static final String WORTH = "RpgWorth";
    private static final String POUCH_INVENTORY = "RpgPouchInventory";
    private static final String POUCH_MOB = "RpgPouchMob";
    private static final EconomyRuntime INSTANCE = new EconomyRuntime();

    private volatile CurrencyKey currency = CurrencyKey.beconomyPrimary();

    private EconomyRuntime() {
        reload();
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(player instanceof ServerPlayerEntity serverPlayer) || world.isClient || !isPouch(stack))
                return TypedActionResult.pass(stack);
            if (stack.getCount() != 1) {
                serverPlayer.sendMessage(Text.literal("§cGold pouches cannot be opened while stacked."), false);
                return TypedActionResult.fail(stack);
            }
            openPouch(serverPlayer, hand, stack);
            return TypedActionResult.success(ItemStack.EMPTY, false);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!(player instanceof ServerPlayerEntity serverPlayer) || world.isClient || !isPouch(stack)) return ActionResult.PASS;
            if (stack.getCount() != 1) {
                serverPlayer.sendMessage(Text.literal("§cGold pouches cannot be opened while stacked."), false);
                return ActionResult.FAIL;
            }
            openPouch(serverPlayer, hand, stack);
            return ActionResult.SUCCESS;
        });
    }

    public static EconomyRuntime instance() {
        return INSTANCE;
    }

    public synchronized void reload() {
        try {
            Map<String, Object> root = map(YamlLite.parse(DefaultFiles.ROOT.resolve("config.yml")));
            Map<String, Object> section = map(root.get("economy"));
            currency = CurrencyKey.parse(string(section.get("currency"), "beconomy:primary"));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load economy config", exception);
        }
    }

    public void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("deposit").executes(ctx -> openDeposit(ctx.getSource().getPlayer())));
    }

    public CurrencyKey currency() {
        return currency;
    }

    public int openDeposit(ServerPlayerEntity player) {
        CurrencyService service = CurrencyService.get();
        if (!service.isAvailable(currency)) {
            player.sendMessage(Text.literal("§cCurrency backend is unavailable: " + currency.serialized()), false);
            return 0;
        }
        new DepositInventory(player, this).open();
        return 1;
    }

    private void openPouch(ServerPlayerEntity player, Hand hand, ItemStack stack) {
        ItemStack detached = stack.copy();
        player.setStackInHand(hand, ItemStack.EMPTY);
        new GoldPouchInventory(player, this, detached, hand).open();
    }

    public static int worth(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        NbtCompound nbt = custom(stack);
        return Math.max(0, nbt.getInt(WORTH));
    }

    public static boolean isPouch(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return custom(stack).contains(POUCH_INVENTORY);
    }

    private static boolean isMobPouch(ItemStack stack) {
        return custom(stack).getBoolean(POUCH_MOB);
    }

    private static NbtCompound custom(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? new NbtCompound() : component.copyNbt();
    }

    private static void setCustom(ItemStack stack, NbtCompound nbt) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static BigDecimal inventoryWorth(SimpleInventory inventory, int limit) {
        BigDecimal total = BigDecimal.ZERO;
        for (int slot = 0; slot < limit; slot++) {
            ItemStack stack = inventory.getStack(slot);
            int worth = worth(stack);
            if (worth > 0 && !stack.isEmpty()) total = total.add(BigDecimal.valueOf((long) worth * stack.getCount()));
        }
        return total;
    }

    private static ItemStack depositButton(BigDecimal worth) {
        ItemStack button = new ItemStack(Items.BOOK);
        button.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§eDeposit §6" + worth.stripTrailingZeros().toPlainString()));
        return button;
    }

    private static void giveBack(ServerPlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<ItemStack> decodePouch(ServerPlayerEntity player, ItemStack pouch) {
        NbtCompound root = custom(pouch);
        NbtList encoded = root.getList(POUCH_INVENTORY, NbtElement.COMPOUND_TYPE);
        ArrayList<ItemStack> result = new ArrayList<>(18);
        DynamicOps<NbtElement> ops = (DynamicOps) player.getRegistryManager().getOps(NbtOps.INSTANCE);
        for (int i = 0; i < 18; i++) {
            if (i >= encoded.size()) {
                result.add(ItemStack.EMPTY);
                continue;
            }
            NbtElement element = encoded.get(i);
            ItemStack stack = ItemStack.CODEC.parse(ops, element).result().orElse(ItemStack.EMPTY);
            result.add(stack);
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void encodePouch(ServerPlayerEntity player, ItemStack pouch, SimpleInventory inventory) {
        NbtCompound root = custom(pouch);
        NbtList encoded = new NbtList();
        DynamicOps<NbtElement> ops = (DynamicOps) player.getRegistryManager().getOps(NbtOps.INSTANCE);
        for (int i = 0; i < 18; i++) {
            ItemStack stack = inventory.getStack(i);
            NbtElement element = ItemStack.CODEC.encodeStart(ops, stack).result().orElseGet(NbtCompound::new);
            encoded.add(element);
        }
        root.put(POUCH_INVENTORY, encoded);
        setCustom(pouch, root);
    }

    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        HashMap<String, Object> result = new HashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static abstract class CurrencyInventory extends PluginInventory {
        final EconomyRuntime runtime;
        SimpleInventory inventory;

        CurrencyInventory(ServerPlayerEntity player, EconomyRuntime runtime) {
            super(player);
            this.runtime = runtime;
        }

        ItemStack cursor() {
            return player.currentScreenHandler.getCursorStack();
        }

        void setCursor(ItemStack stack) {
            player.currentScreenHandler.setCursorStack(stack);
        }

        void moveTopSlot(int slot, boolean acceptInput) {
            if (slot < 0 || slot >= inventory.size()) return;
            ItemStack top = inventory.getStack(slot);
            ItemStack cursor = cursor();
            if (cursor.isEmpty()) {
                if (!top.isEmpty()) {
                    setCursor(top);
                    inventory.setStack(slot, ItemStack.EMPTY);
                    inventory.markDirty();
                }
                return;
            }
            if (!acceptInput || worth(cursor) < 1 || isPouch(cursor)) return;
            if (top.isEmpty()) {
                inventory.setStack(slot, cursor);
                setCursor(ItemStack.EMPTY);
                inventory.markDirty();
                return;
            }
            if (ItemStack.areItemsAndComponentsEqual(top, cursor) && top.getCount() < top.getMaxCount()) {
                int moved = Math.min(cursor.getCount(), top.getMaxCount() - top.getCount());
                top.increment(moved);
                cursor.decrement(moved);
                if (cursor.isEmpty()) setCursor(ItemStack.EMPTY);
                inventory.markDirty();
            }
        }
    }

    private static final class DepositInventory extends CurrencyInventory {
        private static final int BUTTON = 26;
        private boolean deposited;

        DepositInventory(ServerPlayerEntity player, EconomyRuntime runtime) {
            super(player, runtime);
        }

        @Override public String getTitle() { return "Deposit"; }

        @Override
        public SimpleInventory getInventory() {
            inventory = new SimpleInventory(27) {
                @Override public boolean isValid(int slot, ItemStack stack) {
                    return slot != BUTTON && worth(stack) > 0 && !isPouch(stack);
                }
            };
            updateButton();
            return inventory;
        }

        @Override
        public void onClick(Click click) {
            if (click.slot() == BUTTON) {
                deposit();
                return;
            }
            if (click.slot() >= 0 && click.slot() < BUTTON) {
                moveTopSlot(click.slot(), true);
                updateButton();
            }
        }

        @Override
        public void onClose() {
            if (inventory == null || deposited) return;
            for (int i = 0; i < BUTTON; i++) {
                ItemStack stack = inventory.removeStack(i);
                giveBack(player, stack);
            }
        }

        private void updateButton() {
            inventory.setStack(BUTTON, depositButton(inventoryWorth(inventory, BUTTON)));
        }

        private void deposit() {
            BigDecimal total = inventoryWorth(inventory, BUTTON);
            if (total.signum() <= 0) {
                player.sendMessage(Text.literal("§cThere is no currency to deposit."), false);
                return;
            }
            CurrencyService service = CurrencyService.get();
            if (!service.isAvailable(runtime.currency)) {
                player.sendMessage(Text.literal("§cCurrency backend is unavailable: " + runtime.currency.serialized()), false);
                return;
            }
            service.deposit(player, runtime.currency, total);
            deposited = true;
            for (int i = 0; i < BUTTON; i++) inventory.setStack(i, ItemStack.EMPTY);
            player.sendMessage(Text.literal("§aDeposited §6" + total.stripTrailingZeros().toPlainString() + "§a."), false);
            player.closeHandledScreen();
        }
    }

    private static final class GoldPouchInventory extends CurrencyInventory {
        private final ItemStack pouch;
        private final Hand sourceHand;
        private final boolean mob;
        private boolean closed;

        GoldPouchInventory(ServerPlayerEntity player, EconomyRuntime runtime, ItemStack pouch, Hand sourceHand) {
            super(player, runtime);
            this.pouch = pouch;
            this.sourceHand = sourceHand;
            this.mob = isMobPouch(pouch);
        }

        @Override public String getTitle() { return "Gold Pouch"; }

        @Override
        public SimpleInventory getInventory() {
            inventory = new SimpleInventory(18) {
                @Override public boolean isValid(int slot, ItemStack stack) {
                    return !mob && worth(stack) > 0 && !isPouch(stack);
                }
            };
            List<ItemStack> saved = decodePouch(player, pouch);
            for (int i = 0; i < Math.min(18, saved.size()); i++) inventory.setStack(i, saved.get(i));
            return inventory;
        }

        @Override
        public void onClick(Click click) {
            if (click.slot() >= 0 && click.slot() < 18) moveTopSlot(click.slot(), !mob);
        }

        @Override
        public void onClose() {
            if (closed) return;
            closed = true;
            if (mob && inventoryWorth(inventory, 18).signum() <= 0) return;
            encodePouch(player, pouch, inventory);
            ItemStack inHand = player.getStackInHand(sourceHand);
            if (inHand.isEmpty()) player.setStackInHand(sourceHand, pouch);
            else giveBack(player, pouch);
        }
    }
}
