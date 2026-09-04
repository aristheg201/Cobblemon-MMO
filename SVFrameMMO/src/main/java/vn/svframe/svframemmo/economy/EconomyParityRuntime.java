package vn.svframe.svframemmo.economy;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.api.economy.CurrencyKey;
import vn.svframe.svframelib.api.economy.CurrencyService;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.manager.ConfigItemManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Adds the original physical-currency withdraw flow to the native economy runtime. */
public final class EconomyParityRuntime {
    private static final String POUCH_INVENTORY = "RpgPouchInventory";
    private static final String POUCH_MOB = "RpgPouchMob";
    private static final long WITHDRAW_TIMEOUT_TICKS = 20L * 20L;
    private static final EconomyParityRuntime INSTANCE = new EconomyParityRuntime();

    private final AtomicBoolean installed = new AtomicBoolean();
    private final Map<UUID, WithdrawSession> withdrawing = new ConcurrentHashMap<>();

    private EconomyParityRuntime() { }

    public static EconomyParityRuntime instance() { return INSTANCE; }

    public void install() {
        if (!installed.compareAndSet(false, true)) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommand(dispatcher));
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, parameters) -> {
            if (!withdrawing.containsKey(sender.getUuid())) return true;
            String input = message.getContent().getString();
            MinecraftServer server = sender.getServer();
            if (server.isOnThread()) consumeWithdrawChat(sender, input);
            else server.execute(() -> consumeWithdrawChat(sender, input));
            return false;
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> withdrawing.remove(handler.player.getUuid()));
    }

    private void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("withdraw")
                .executes(context -> beginWithdraw(context.getSource().getPlayerOrThrow()))
                .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> withdrawNow(context.getSource().getPlayerOrThrow(),
                                IntegerArgumentType.getInteger(context, "amount")))));
    }

    public int beginWithdraw(ServerPlayerEntity player) {
        if (withdrawing.containsKey(player.getUuid())) return 1;
        withdrawing.put(player.getUuid(), new WithdrawSession(player.getBlockPos().asLong(),
                SVFrameMMO.currentTick() + WITHDRAW_TIMEOUT_TICKS));
        player.sendMessage(Text.literal("§eType the amount to withdraw in chat. Move to cancel."), false);
        return 1;
    }

    /** Invalid input and insufficient funds deliberately keep the original chat session open. */
    public boolean consumeWithdrawChat(ServerPlayerEntity player, String input) {
        WithdrawSession session = withdrawing.get(player.getUuid());
        if (session == null) return false;
        final int amount;
        try {
            amount = Integer.parseInt(input.trim());
        } catch (RuntimeException exception) {
            player.sendMessage(Text.literal("§cInvalid withdraw amount: " + input), false);
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(Text.literal("§cWithdraw amount must be greater than zero."), false);
            return true;
        }
        if (withdrawNow(player, amount) == 1) withdrawing.remove(player.getUuid(), session);
        return true;
    }

    public int withdrawNow(ServerPlayerEntity player, int amount) {
        if (amount <= 0) {
            player.sendMessage(Text.literal("§cWithdraw amount must be greater than zero."), false);
            return 0;
        }
        CurrencyKey currency = EconomyRuntime.instance().currency();
        CurrencyService service = CurrencyService.get();
        if (!service.isAvailable(currency)) {
            player.sendMessage(Text.literal("§cCurrency backend is unavailable: " + currency.serialized()), false);
            return 0;
        }

        // Build the whole payout first. A broken item template can never debit a player's balance.
        List<ItemStack> payout = buildWithdrawItems(amount);
        BigDecimal value = BigDecimal.valueOf(amount);
        if (!service.withdraw(player, currency, value)) {
            BigDecimal missing = value.subtract(service.balance(player, currency)).max(BigDecimal.ZERO);
            player.sendMessage(Text.literal("§cYou need §6" + missing.stripTrailingZeros().toPlainString() + "§c more."), false);
            return 0;
        }
        for (ItemStack stack : payout) smartGive(player, stack);
        player.sendMessage(Text.literal("§aWithdrew §6" + amount + "§a."), false);
        return 1;
    }

    private void tick(MinecraftServer server) {
        if (withdrawing.isEmpty()) return;
        long now = SVFrameMMO.currentTick();
        for (Map.Entry<UUID, WithdrawSession> entry : List.copyOf(withdrawing.entrySet())) {
            WithdrawSession session = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                withdrawing.remove(entry.getKey(), session);
                continue;
            }
            if (player.getBlockPos().asLong() != session.startBlock()) {
                if (withdrawing.remove(entry.getKey(), session))
                    player.sendMessage(Text.literal("§cWithdraw cancelled because you moved."), false);
                continue;
            }
            if (now >= session.expiresAt()) withdrawing.remove(entry.getKey(), session);
        }
    }

    private static List<ItemStack> buildWithdrawItems(int worth) {
        int note = worth / 10 * 10;
        int coins = worth - note;
        ConfigItemManager items = ConfigItemManager.instance();
        ArrayList<ItemStack> payout = new ArrayList<>(2);
        if (note > 0) payout.add(items.buildCurrency("NOTE", note));
        if (coins > 0) {
            ItemStack coinStack = items.buildCurrency("GOLD_COIN", 1);
            coinStack.setCount(coins);
            payout.add(coinStack);
        }
        return List.copyOf(payout);
    }

    /** Template-backed pouch factory used by admin/resource surfaces. */
    public ItemStack buildGoldPouch(boolean mob) {
        ItemStack stack = ConfigItemManager.instance().build(mob ? "MOB_GOLD_POUCH" : "GOLD_POUCH");
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = component == null ? new NbtCompound() : component.copyNbt();
        nbt.put(POUCH_INVENTORY, new NbtList());
        nbt.putBoolean(POUCH_MOB, mob);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return stack;
    }

    private static void smartGive(ServerPlayerEntity player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) player.dropItem(stack, false);
    }

    private record WithdrawSession(long startBlock, long expiresAt) { }
}
