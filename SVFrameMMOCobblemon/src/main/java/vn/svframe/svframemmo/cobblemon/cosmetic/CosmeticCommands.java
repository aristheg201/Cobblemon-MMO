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

/** Player GUI plus explicit admin ownership commands with live definition suggestions. */
public final class CosmeticCommands {
    public static final String USE = "svframemmo.cobblemon.cosmetic";
    private CosmeticCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CosmeticService cosmetics) {
        CosmeticGui gui = new CosmeticGui(cosmetics);
        dispatcher.register(CommandManager.literal("cosmetics")
                .requires(source -> source.getEntity() == null || (source.getEntity() instanceof ServerPlayerEntity player && LuckPermsIntegration.has(player, USE)))
                .executes(ctx -> { gui.open(ctx.getSource().getPlayerOrThrow()); return 1; })
                .then(CommandManager.literal("preview").then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(cosmetics.owned(ctx.getSource().getPlayerOrThrow().getUuid()), builder))
                        .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    CosmeticService.Result result = cosmetics.preview(player, StringArgumentType.getString(ctx, "id"));
                    player.sendMessage(Text.literal(result.success() ? "Cosmetic preview started." : result.message()), true);
                    return result.success() ? 1 : 0;
                })))
                .then(CommandManager.literal("equip").then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(cosmetics.owned(ctx.getSource().getPlayerOrThrow().getUuid()), builder))
                        .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    CosmeticService.Result result = cosmetics.equip(player, StringArgumentType.getString(ctx, "id"));
                    player.sendMessage(Text.literal(result.success() ? "Cosmetic equipped." : result.message()), true);
                    return result.success() ? 1 : 0;
                })))
                .then(CommandManager.literal("unequip").then(CommandManager.argument("skill", StringArgumentType.word())
                        .suggests((ctx, builder) -> CommandSource.suggestMatching(cosmetics.equipped(ctx.getSource().getPlayerOrThrow().getUuid()).keySet(), builder))
                        .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                    boolean changed = cosmetics.unequip(player, StringArgumentType.getString(ctx, "skill"));
                    player.sendMessage(Text.literal(changed ? "Cosmetic unequipped." : "No cosmetic is equipped for that skill."), true);
                    return changed ? 1 : 0;
                })))
                .then(CommandManager.literal("grant").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(cosmetics.definitions().stream().map(CosmeticDefinition::id).toList(), builder))
                                        .executes(ctx -> {
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    boolean changed = cosmetics.grant(target.getUuid(), StringArgumentType.getString(ctx, "id"));
                                    ctx.getSource().sendFeedback(() -> Text.literal(changed ? "Cosmetic granted." : "Cosmetic was already owned or unknown."), false);
                                    return changed ? 1 : 0;
                                }))))
                .then(CommandManager.literal("revoke").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> CommandSource.suggestMatching(cosmetics.definitions().stream().map(CosmeticDefinition::id).toList(), builder))
                                        .executes(ctx -> {
                                    ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                    boolean changed = cosmetics.revoke(target.getUuid(), StringArgumentType.getString(ctx, "id"));
                                    ctx.getSource().sendFeedback(() -> Text.literal(changed ? "Cosmetic revoked." : "Cosmetic was not owned or unknown."), false);
                                    return changed ? 1 : 0;
                                })))));
    }
}
