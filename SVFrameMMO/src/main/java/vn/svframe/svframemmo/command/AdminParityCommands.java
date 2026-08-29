package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.manager.ConfigItemManager;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Restores the native admin currency and RPG resource command surface. */
public final class AdminParityCommands implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("svframemmo")
                .then(literal("coins").requires(source -> source.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> giveCoins(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                                IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(literal("admin").requires(source -> source.hasPermissionLevel(2))
                        .then(resourceTree("health", PlayerResource.HEALTH))
                        .then(resourceTree("mana", PlayerResource.MANA))
                        .then(resourceTree("stamina", PlayerResource.STAMINA))
                        .then(resourceTree("stellium", PlayerResource.STELLIUM))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> resourceTree(
            String type, PlayerResource resource) {
        return literal("resource-" + type)
                .then(resourceAction("set", resource, ResourceOperation.SET, type))
                .then(resourceAction("give", resource, ResourceOperation.GIVE, type))
                .then(resourceAction("take", resource, ResourceOperation.TAKE, type));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> resourceAction(
            String name, PlayerResource resource, ResourceOperation operation, String type) {
        return literal(name)
                .then(argument("player", EntityArgumentType.player())
                        .then(argument("amount", DoubleArgumentType.doubleArg())
                                .executes(ctx -> editResource(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                        DoubleArgumentType.getDouble(ctx, "amount"), resource, operation, type))));
    }

    private static int giveCoins(ServerCommandSource source, ServerPlayerEntity player, int amount) {
        ItemStack template = ConfigItemManager.instance().buildCurrency("GOLD_COIN", 1);
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = template.copy();
            int count = Math.min(remaining, stack.getMaxCount());
            stack.setCount(count);
            smartGive(player, stack);
            remaining -= count;
        }
        source.sendFeedback(() -> Text.literal("Gave " + amount + " gold coins to " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int editResource(ServerCommandSource source, ServerPlayerEntity player, double amount,
                                    PlayerResource resource, ResourceOperation operation, String type) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        switch (operation) {
            case SET -> resource.setCurrent(data, amount, ResourceUpdateReason.COMMAND);
            case GIVE -> resource.give(data, amount, ResourceUpdateReason.COMMAND);
            case TAKE -> resource.give(data, -amount, ResourceUpdateReason.COMMAND);
        }
        source.sendFeedback(() -> Text.literal(player.getName().getString() + " now has "
                + trim(resource.getCurrent(data)) + " " + type + " points."), true);
        return 1;
    }

    private static void smartGive(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) player.dropItem(stack, false);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private enum ResourceOperation { SET, GIVE, TAKE }
}
