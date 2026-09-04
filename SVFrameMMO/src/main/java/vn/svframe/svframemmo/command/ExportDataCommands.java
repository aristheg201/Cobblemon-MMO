package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.persistence.PersistenceConfig;

import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native storage export command. Exporting never changes the live backend. */
public final class ExportDataCommands implements ModInitializer {
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("svframemmo").then(literal("admin").requires(source -> source.hasPermissionLevel(2))
                .then(literal("exportdata")
                        .executes(ctx -> export(ctx.getSource(), opposite()))
                        .then(argument("backend", StringArgumentType.word())
                                .suggests((ctx, builder) -> { builder.suggest("yaml"); builder.suggest("mysql"); builder.suggest("json"); return builder.buildFuture(); })
                                .executes(ctx -> exportNamed(ctx.getSource(), StringArgumentType.getString(ctx, "backend")))))));
    }

    private static PersistenceConfig.Backend opposite() {
        return "MYSQL".equalsIgnoreCase(SVFrameMMO.playerData().backendName())
                ? PersistenceConfig.Backend.YAML : PersistenceConfig.Backend.MYSQL;
    }

    private static int exportNamed(ServerCommandSource source, String input) {
        try { return export(source, PersistenceConfig.Backend.valueOf(input.trim().toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException exception) {
            source.sendError(Text.literal("Unknown backend '" + input + "'. Use yaml, mysql or json."));
            return 0;
        }
    }

    private static int export(ServerCommandSource source, PersistenceConfig.Backend target) {
        try {
            if (SVFrameMMO.playerData().backendName().equalsIgnoreCase(target.name())) {
                source.sendError(Text.literal("Target backend is already live: " + target));
                return 0;
            }
            int records = SVFrameMMO.playerData().exportTo(target);
            source.sendFeedback(() -> Text.literal("Exported " + records + " userdata record(s) to " + target + ". Live backend remains "
                    + SVFrameMMO.playerData().backendName() + "."), true);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("Userdata export failed: " + exception.getMessage()));
            return 0;
        }
    }
}
