package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Locale;

/** Executes validated reward records from quest JSON. No quest id or reward bundle is hardcoded here. */
public final class RewardDispatcher {
    public void grant(ServerPlayerEntity player, QuestCatalog.Quest quest) {
        boolean failed = false;
        for (QuestCatalog.Reward reward : quest.grants()) {
            try {
                dispatch(player, reward);
            } catch (Throwable error) {
                failed = true;
                SVQuest.LOGGER.error("Reward '{}' failed for {} / {}", reward.type(), player.getName().getString(), quest.id(), error);
            }
        }
        player.sendMessage(Text.literal("§a✓ Hoàn thành: §f" + quest.title()), false);
        if (failed) player.sendMessage(Text.literal("§eMột phần thưởng gặp lỗi. Tiến trình đã được ghi nhận; hãy báo admin."), false);
    }

    private static void dispatch(ServerPlayerEntity player, QuestCatalog.Reward reward) throws Exception {
        switch (reward.type()) {
            case "item" -> item(player, reward.id(), reward.count());
            case "command" -> command(player, substitute(reward.command(), player));
            case "cobbledollars" -> cobbleDollars(player, reward.amount());
            case "beconomy" -> bEconomy(player, reward.id(), reward.amount());
            case "svframemmo_point" -> svFramePoint(player, reward.point(), reward.amount());
            default -> throw new IllegalArgumentException("Unknown reward type: " + reward.type());
        }
    }

    private static void item(ServerPlayerEntity player, String itemId, int count) {
        if (itemId == null || itemId.isBlank() || count <= 0) return;
        command(player, "give " + player.getName().getString() + " " + itemId + " " + count);
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
}
