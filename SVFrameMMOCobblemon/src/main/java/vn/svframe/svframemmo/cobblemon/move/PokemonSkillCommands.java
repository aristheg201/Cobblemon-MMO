package vn.svframe.svframemmo.cobblemon.move;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** /pokeskill and /pokemonskill: player shop plus explicit admin grants. */
public final class PokemonSkillCommands {
    private PokemonSkillCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, PokemonSkillShopService shop) {
        registerRoot(dispatcher, "pokeskill", shop);
        registerRoot(dispatcher, "pokemonskill", shop);
    }

    private static void registerRoot(CommandDispatcher<ServerCommandSource> dispatcher, String name, PokemonSkillShopService shop) {
        dispatcher.register(literal(name)
                .executes(context -> open(context.getSource(), shop))
                .then(literal("admin")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(literal("give")
                                .then(argument("player", EntityArgumentType.player())
                                        .then(argument("skill", StringArgumentType.word())
                                                .suggests((context, builder) -> suggestSkills(shop, builder))
                                                .executes(context -> give(context, shop)))))));
    }

    private static int open(ServerCommandSource source, PokemonSkillShopService shop) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        shop.open(source.getPlayerOrThrow());
        return 1;
    }

    private static int give(CommandContext<ServerCommandSource> context, PokemonSkillShopService shop)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        String moveId = StringArgumentType.getString(context, "skill");
        PokemonSkillShopService.GrantResult result = shop.adminGive(target, moveId);
        if (!result.success()) {
            context.getSource().sendError(Text.literal(result.message()));
            return 0;
        }
        context.getSource().sendFeedback(() -> Text.literal(result.message()), true);
        target.sendMessage(Text.literal("Admin granted Pokemon skill: " + result.skillName() + ". Use /mmo skills to bind it."), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestSkills(PokemonSkillShopService shop, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        shop.moveIds().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(remaining))
                .forEach(builder::suggest);
        return builder.buildFuture();
    }
}
