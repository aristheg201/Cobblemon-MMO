package vn.svframe.svframemmo.cobblemon.cosmetic;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;

import java.util.Arrays;

/** Player GUI plus explicit admin ownership commands with live data-driven definition suggestions. */
public final class CosmeticCommands {
    public static final String USE = "svframemmo.cobblemon.cosmetic";

    private CosmeticCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CosmeticService cosmetics) {
        CosmeticGui gui = new CosmeticGui(cosmetics);
        dispatcher.register(CommandManager.literal("cosmetics")
                .requires(source -> source.getEntity() == null
                        || (source.getEntity() instanceof ServerPlayerEntity player
                        && LuckPermsIntegration.has(player, USE)))
                .executes(ctx -> {
                    gui.open(ctx.getSource().getPlayerOrThrow());
                    return 1;
                })
                .then(CommandManager.literal("preview")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                        cosmetics.owned(ctx.getSource().getPlayerOrThrow().getUuid()), builder))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    CosmeticService.Result result = cosmetics.preview(
                                            player, StringArgumentType.getString(ctx, "id"));
                                    player.sendMessage(Text.literal(result.success()
                                            ? "Cosmetic preview started."
                                            : result.message()), true);
                                    return result.success() ? 1 : 0;
                                })))
                .then(CommandManager.literal("equip")
                        .then(CommandManager.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                        cosmetics.owned(ctx.getSource().getPlayerOrThrow().getUuid()), builder))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    CosmeticService.Result result = cosmetics.equip(
                                            player, StringArgumentType.getString(ctx, "id"));
                                    player.sendMessage(Text.literal(result.success()
                                            ? "Cosmetic equipped in " + result.definition().slot().id() + "."
                                            : result.message()), true);
                                    return result.success() ? 1 : 0;
                                })))
                .then(CommandManager.literal("unequip")
                        .then(CommandManager.argument("slot", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                        cosmetics.equipped(ctx.getSource().getPlayerOrThrow().getUuid())
                                                .keySet().stream().map(CosmeticDefinition.Slot::id).toList(),
                                        builder))
                                .executes(ctx -> {
                                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                                    CosmeticDefinition.Slot slot = CosmeticDefinition.Slot.tryParse(
                                            StringArgumentType.getString(ctx, "slot"));
                                    boolean changed = cosmetics.unequip(player, slot);
                                    player.sendMessage(Text.literal(changed
                                            ? "Cosmetic unequipped."
                                            : "No cosmetic is equipped in that slot."), true);
                                    return changed ? 1 : 0;
                                })))
                .then(CommandManager.literal("slots")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "Cosmetic slots: " + String.join(", ",
                                            Arrays.stream(CosmeticDefinition.Slot.values())
                                                    .map(CosmeticDefinition.Slot::id).toList())), false);
                            return 1;
                        }))
                .then(CommandManager.literal("grant").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                                cosmetics.definitions().stream()
                                                        .map(CosmeticDefinition::id).toList(), builder))
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            boolean changed = cosmetics.grant(
                                                    target.getUuid(), StringArgumentType.getString(ctx, "id"));
                                            ctx.getSource().sendFeedback(() -> Text.literal(changed
                                                    ? "Cosmetic granted."
                                                    : "Cosmetic was already owned or unknown."), false);
                                            return changed ? 1 : 0;
                                        }))))
                .then(CommandManager.literal("revoke").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                                cosmetics.definitions().stream()
                                                        .map(CosmeticDefinition::id).toList(), builder))
                                        .executes(ctx -> {
                                            ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                            boolean changed = cosmetics.revoke(
                                                    target.getUuid(), StringArgumentType.getString(ctx, "id"));
                                            ctx.getSource().sendFeedback(() -> Text.literal(changed
                                                    ? "Cosmetic revoked."
                                                    : "Cosmetic was not owned or unknown."), false);
                                            return changed ? 1 : 0;
                                        })))));
    }
}
