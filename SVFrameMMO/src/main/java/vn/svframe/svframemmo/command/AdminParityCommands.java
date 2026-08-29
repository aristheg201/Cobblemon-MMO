package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.message.actionbar.ActionBarPriority;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.experience.Booster;
import vn.svframe.svframemmo.experience.Profession;
import vn.svframe.svframemmo.manager.ConfigItemManager;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Restores native admin currency, resources, boosters and utility command surfaces. */
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
                .then(boosterTree())
                .then(literal("admin").requires(source -> source.hasPermissionLevel(2))
                        .then(resourceTree("health", PlayerResource.HEALTH))
                        .then(resourceTree("mana", PlayerResource.MANA))
                        .then(resourceTree("stamina", PlayerResource.STAMINA))
                        .then(resourceTree("stellium", PlayerResource.STELLIUM))
                        .then(literal("hideab")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("duration", LongArgumentType.longArg(0L))
                                                .executes(ctx -> hideActionBar(EntityArgumentType.getPlayer(ctx, "player"),
                                                        LongArgumentType.getLong(ctx, "duration"))))))
                        .then(literal("info")
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> info(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> boosterTree() {
        return literal("booster").requires(source -> source.hasPermissionLevel(2))
                .then(literal("create")
                        .then(argument("profession", StringArgumentType.word())
                                .suggests(AdminParityCommands::suggestProfessionsOrMain)
                                .then(argument("extra", DoubleArgumentType.doubleArg())
                                        .then(argument("duration", LongArgumentType.longArg(0L))
                                                .executes(ctx -> createBooster(ctx.getSource(), StringArgumentType.getString(ctx, "profession"),
                                                        DoubleArgumentType.getDouble(ctx, "extra"), LongArgumentType.getLong(ctx, "duration"), null))
                                                .then(argument("author", StringArgumentType.word())
                                                        .executes(ctx -> createBooster(ctx.getSource(), StringArgumentType.getString(ctx, "profession"),
                                                                DoubleArgumentType.getDouble(ctx, "extra"), LongArgumentType.getLong(ctx, "duration"),
                                                                StringArgumentType.getString(ctx, "author"))))))))
                .then(literal("list").executes(ctx -> listBoosters(ctx.getSource())))
                .then(literal("remove")
                        .then(argument("booster_id", StringArgumentType.word())
                                .suggests(AdminParityCommands::suggestBoosters)
                                .executes(ctx -> removeBooster(ctx.getSource(), StringArgumentType.getString(ctx, "booster_id")))));
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

    private static int createBooster(ServerCommandSource source, String professionId, double extra, long durationSeconds, String author) {
        Profession profession = null;
        if (!professionId.equalsIgnoreCase("main")) {
            profession = SVFrameMMO.professions().get(professionId);
            if (profession == null) {
                source.sendError(Text.literal("Unknown profession '" + professionId + "'."));
                return 0;
            }
        }
        String target = profession == null ? null : profession.getKey();
        Booster booster = new Booster(author, target, extra, durationSeconds);
        SVFrameMMO.boosters().register(booster);
        String targetName = profession == null ? "main" : profession.getName();
        String message = "New " + trim(1d + extra) + "x EXP booster for " + targetName + " (" + durationSeconds + "s).";
        for (ServerPlayerEntity online : source.getServer().getPlayerManager().getPlayerList()) online.sendMessage(Text.literal(message), false);
        return 1;
    }

    private static int listBoosters(ServerCommandSource source) {
        var active = SVFrameMMO.boosters().getActive();
        source.sendFeedback(() -> Text.literal("Active boosters: " + active.size()), false);
        for (Booster booster : active) {
            String target = booster.getTargetKey() == null ? "main" : booster.getTargetKey();
            source.sendFeedback(() -> Text.literal(booster.getUniqueId() + " | " + trim(1d + booster.getExtra())
                    + "x | " + target + " | " + formatDuration(booster.getLeft())
                    + (booster.getAuthor() == null ? "" : " | author=" + booster.getAuthor())), false);
        }
        return 1;
    }

    private static int removeBooster(ServerCommandSource source, String input) {
        final UUID id;
        try { id = UUID.fromString(input); }
        catch (IllegalArgumentException exception) {
            source.sendError(Text.literal("Invalid booster UUID '" + input + "'."));
            return 0;
        }
        if (!SVFrameMMO.boosters().unregister(id)) {
            source.sendError(Text.literal("Could not find active booster '" + id + "'."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal("Successfully unregistered booster " + id + "."), true);
        return 1;
    }

    private static int hideActionBar(ServerPlayerEntity player, long durationTicks) {
        SVFrameMMO.playerData().get(player).getMMOPlayerData().getActionBar().hide(ActionBarPriority.LOWEST, durationTicks);
        return 1;
    }

    private static int info(ServerCommandSource source, ServerPlayerEntity player) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        source.sendFeedback(() -> Text.literal("Class: " + data.getProfess().getName()), false);
        source.sendFeedback(() -> Text.literal("Level: " + data.getLevel()), false);
        source.sendFeedback(() -> Text.literal("Experience: " + trim(data.getExperience()) + " / " + data.getLevelUpExperience()), false);
        source.sendFeedback(() -> Text.literal("Class Points: " + data.getClassPoints()), false);
        for (Profession profession : SVFrameMMO.professions().getAll())
            source.sendFeedback(() -> Text.literal(profession.getName() + ": Lvl " + data.getProfessions().getLevel(profession)
                    + " - " + trim(data.getProfessions().getExperience(profession)) + " / "
                    + data.getProfessions().getLevelUpExperience(profession)), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestProfessionsOrMain(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        if ("main".startsWith(remaining)) builder.suggest("main");
        SVFrameMMO.professions().getAll().stream().map(Profession::getId)
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining)).sorted(String.CASE_INSENSITIVE_ORDER).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestBoosters(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        SVFrameMMO.boosters().getActive().stream().map(booster -> booster.getUniqueId().toString())
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining)).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static void smartGive(ServerPlayerEntity player, ItemStack stack) {
        if (!player.getInventory().insertStack(stack) && !stack.isEmpty()) player.dropItem(stack, false);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long remainder = seconds % 60L;
        return hours > 0 ? hours + "h " + minutes + "m " + remainder + "s"
                : minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private enum ResourceOperation { SET, GIVE, TAKE }
}
