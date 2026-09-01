package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Executes validated reward records from quest JSON. No quest id or reward bundle is hardcoded here. */
public final class RewardDispatcher {
    /**
     * Full-inventory is a hard block: no reward side effect is executed and the quest remains claimable.
     * Integration failures after inventory preflight are reported but not retried automatically, preventing duplicate payouts.
     */
    public ClaimResult claim(ServerPlayerEntity player, QuestCatalog.Quest quest) {
        String inventoryBlock = preflightItems(player, quest);
        if (inventoryBlock != null) return ClaimResult.blocked(inventoryBlock);

        boolean failed = false;
        // Item rewards are inserted first, immediately after the capacity simulation, before any external reward can alter inventory.
        for (QuestCatalog.Reward reward : quest.grants()) {
            if (!"item".equals(reward.type())) continue;
            try {
                item(player, reward.id(), reward.count());
            } catch (Throwable error) {
                failed = true;
                SVQuest.LOGGER.error("Item reward '{}' failed for {} / {}", reward.id(), player.getName().getString(), quest.id(), error);
            }
        }
        for (QuestCatalog.Reward reward : quest.grants()) {
            if ("item".equals(reward.type())) continue;
            try {
                dispatchNonItem(player, reward);
            } catch (Throwable error) {
                failed = true;
                SVQuest.LOGGER.error("Reward '{}' failed for {} / {}", reward.type(), player.getName().getString(), quest.id(), error);
            }
        }
        return new ClaimResult(true, failed, "");
    }

    private static String preflightItems(ServerPlayerEntity player, QuestCatalog.Quest quest) {
        try {
            List<ItemStack> virtual = new ArrayList<>(36);
            for (int slot = 0; slot < 36; slot++) virtual.add(player.getInventory().getStack(slot).copy());

            for (QuestCatalog.Reward reward : quest.grants()) {
                if (!"item".equals(reward.type()) || reward.count() <= 0) continue;
                Item item = resolveItem(reward.id());
                ItemStack template = new ItemStack(item);
                int remaining = reward.count();

                for (ItemStack stack : virtual) {
                    if (remaining <= 0) break;
                    if (stack.isEmpty() || !ItemStack.areItemsAndComponentsEqual(stack, template)) continue;
                    int room = Math.max(0, stack.getMaxCount() - stack.getCount());
                    if (room <= 0) continue;
                    int add = Math.min(room, remaining);
                    stack.increment(add);
                    remaining -= add;
                }

                for (int slot = 0; slot < virtual.size() && remaining > 0; slot++) {
                    if (!virtual.get(slot).isEmpty()) continue;
                    int add = Math.min(template.getMaxCount(), remaining);
                    virtual.set(slot, new ItemStack(item, add));
                    remaining -= add;
                }

                if (remaining > 0) {
                    return "§cTúi đồ không đủ chỗ để nhận toàn bộ phần thưởng. Hãy dọn túi rồi bấm NHẬN THƯỞNG lại.";
                }
            }
            return null;
        } catch (Throwable error) {
            SVQuest.LOGGER.error("Reward inventory preflight failed for {} / {}", player.getName().getString(), quest.id(), error);
            return "§cKhông thể kiểm tra chỗ trống trong túi đồ. Phần thưởng vẫn được giữ lại; hãy báo admin.";
        }
    }

    private static void dispatchNonItem(ServerPlayerEntity player, QuestCatalog.Reward reward) throws Exception {
        switch (reward.type()) {
            case "command" -> command(player, substitute(reward.command(), player));
            case "cobbledollars" -> cobbleDollars(player, reward.amount());
            case "beconomy" -> bEconomy(player, reward.id(), reward.amount());
            case "svframemmo_point" -> svFramePoint(player, reward.point(), reward.amount());
            default -> throw new IllegalArgumentException("Unknown reward type: " + reward.type());
        }
    }

    private static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("Blank reward item id");
        Identifier id = Identifier.tryParse(itemId);
        if (id == null) throw new IllegalArgumentException("Invalid reward item id: " + itemId);
        Item item = Registries.ITEM.get(id);
        if (item == null || item == Items.AIR) throw new IllegalArgumentException("Unknown reward item: " + itemId);
        return item;
    }

    private static void item(ServerPlayerEntity player, String itemId, int count) {
        if (count <= 0) return;
        Item item = resolveItem(itemId);
        int remaining = count;
        int max = new ItemStack(item).getMaxCount();
        while (remaining > 0) {
            int amount = Math.min(max, remaining);
            ItemStack stack = new ItemStack(item, amount);
            boolean inserted = player.getInventory().insertStack(stack);
            if (!inserted || !stack.isEmpty()) {
                throw new IllegalStateException("Inventory changed after successful reward preflight for " + itemId);
            }
            remaining -= amount;
        }
    }

    private static void command(ServerPlayerEntity player, String command) {
        if (command == null || command.isBlank()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), command);
    }

    private static String substitute(String command, ServerPlayerEntity player) {
        if (command == null) return "";
        String name = player.getName().getString();
        return command.replace("{player}", name).replace("%player%", name).replace("{uuid}", player.getUuidAsString());
    }

    private static void cobbleDollars(ServerPlayerEntity player, double amount) {
        long value = Math.max(0L, Math.round(amount));
        if (value == 0 || !FabricLoader.getInstance().isModLoaded("cobbledollars")) return;
        command(player, "cobbledollars give " + player.getName().getString() + " " + value);
    }

    private static void bEconomy(ServerPlayerEntity player, String currency, double amount) throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("beconomy") || amount <= 0 || currency == null || currency.isBlank()) return;
        Class<?> root = Class.forName("org.krripe.beconomy.api.BEconomy");
        Object instance = root.getField("INSTANCE").get(null);
        Object api = root.getMethod("getAPI").invoke(instance);
        if (api == null) return;
        Method addBalance = api.getClass().getMethod("addBalance", java.util.UUID.class, BigDecimal.class, String.class);
        addBalance.invoke(api, player.getUuid(), BigDecimal.valueOf(amount), currency);
    }

    private static void svFramePoint(ServerPlayerEntity player, String point, double amount) throws ReflectiveOperationException {
        int value = Math.max(0, (int) Math.round(amount));
        if (!FabricLoader.getInstance().isModLoaded("svframemmo") || value <= 0) return;
        Class<?> root = Class.forName("vn.svframe.svframemmo.SVFrameMMO");
        Object manager = root.getMethod("playerData").invoke(null);
        Object data = manager.getClass().getMethod("get", ServerPlayerEntity.class).invoke(manager, player);
        if (data == null) return;

        String method = switch ((point == null ? "" : point).toLowerCase(Locale.ROOT)) {
            case "skill", "skill_point", "skill_points" -> "giveSkillPoints";
            case "attribute", "attribute_point", "attribute_points" -> "giveAttributePoints";
            case "class", "class_point", "class_points" -> "giveClassPoints";
            default -> throw new IllegalArgumentException("Unknown SVFrameMMO point type: " + point);
        };
        data.getClass().getMethod(method, int.class).invoke(data, value);
    }

    public record ClaimResult(boolean granted, boolean partialFailure, String message) {
        public static ClaimResult blocked(String message) { return new ClaimResult(false, false, message); }
    }
}
