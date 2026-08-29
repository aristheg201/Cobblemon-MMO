package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.persistence.LegacyYamlImporter;

import java.nio.file.Path;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Administrative migration command for original flat YAML userdata. */
public final class ImportParityCommands implements ModInitializer {
    private static final LegacyYamlImporter IMPORTER = new LegacyYamlImporter();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("svframemmo")
                .then(literal("admin").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("import-yaml")
                                .executes(ctx -> importYaml(ctx.getSource(), Path.of("plugins", "MMO" + "Core", "userdata")))
                                .then(argument("directory", StringArgumentType.greedyString())
                                        .executes(ctx -> importYaml(ctx.getSource(), Path.of(StringArgumentType.getString(ctx, "directory"))))))));
    }

    private static int importYaml(ServerCommandSource source, Path directory) {
        try {
            LegacyYamlImporter.ImportResult result = IMPORTER.importDirectory(directory.toAbsolutePath().normalize());
            source.sendFeedback(() -> Text.literal("Legacy YAML import: imported=" + result.imported()
                    + ", skipped-online=" + result.skippedOnline() + ", failed=" + result.failed() + "."), true);
            for (String error : result.errors()) source.sendError(Text.literal(error));
            return result.failed() == 0 && result.skippedOnline() == 0 ? 1 : 0;
        } catch (Exception exception) {
            source.sendError(Text.literal("Legacy YAML import failed: " + exception.getMessage()));
            return 0;
        }
    }
}
