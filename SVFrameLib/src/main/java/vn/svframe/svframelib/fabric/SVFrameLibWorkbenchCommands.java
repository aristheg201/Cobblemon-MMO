package vn.svframe.svframelib.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Exact SVFrameLib 1.7.1 Super/Mega workbench command surface. */
final class SVFrameLibWorkbenchCommands {
    private static final String SUPER = "svframelib.superworkbench";
    private static final String MEGA = "svframelib.megaworkbench";

    private SVFrameLibWorkbenchCommands() {}

    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(root("svframelib"));
        dispatcher.register(root("ml"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> root(String name) {
        return literal(name)
                .then(workbench("superworkbench", SUPER, true))
                .then(workbench("megaworkbench", MEGA, false));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> workbench(String name, String permission, boolean superWorkbench) {
        return literal(name)
                .requires(source -> permitted(source, permission))
                .executes(ctx -> open(ctx.getSource(), null, superWorkbench))
                .then(argument("player", EntityArgumentType.player())
                        .executes(ctx -> open(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), superWorkbench)));
    }

    private static int open(ServerCommandSource source, ServerPlayerEntity target, boolean superWorkbench)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = target == null ? source.getPlayerOrThrow() : target;
        boolean opened = superWorkbench ? SVFrameLibWorkbenchMod.openSuper(player) : SVFrameLibWorkbenchMod.openMega(player);
        if (!opened) source.sendError(Text.literal("Could not open SVFrameLib workbench."));
        return opened ? 1 : 0;
    }

    private static boolean permitted(ServerCommandSource source, String permission) {
        if (source.getEntity() instanceof ServerPlayerEntity player) return SVFrameLibPermissionBridge.has(player, permission);
        return source.hasPermissionLevel(2);
    }
}
