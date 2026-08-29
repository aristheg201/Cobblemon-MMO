package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.player.PlayerResetRuntime;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native save/reset admin commands corresponding to the original MMOCore administrative tree. */
public final class ResetParityCommands implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("svframemmo")
                .then(literal("admin").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("savedata")
                                .then(argument("player", EntityArgumentType.player())
                                        .executes(ctx -> saveData(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player")))))
                        .then(resetTree())));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> resetTree() {
        return literal("reset")
                .then(literal("classes")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.CLASSES, false))))
                .then(literal("levels")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.LEVELS, false))))
                .then(literal("skills")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.SKILLS, false))))
                .then(literal("attributes")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.ATTRIBUTES, false))
                                .then(literal("-reallocate")
                                        .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.ATTRIBUTES, true)))))
                .then(literal("skill-trees")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.SKILL_TREES, false))))
                .then(literal("all")
                        .then(argument("player", EntityArgumentType.player())
                                .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.ALL, false))
                                .then(literal("-reallocate")
                                        .executes(ctx -> reset(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), ResetKind.ALL, true)))));
    }

    private static int saveData(ServerCommandSource source, ServerPlayerEntity player) {
        // The native store is an atomic single-file database, so a targeted save flushes the complete coherent snapshot.
        SVFrameMMO.playerData().save();
        source.sendFeedback(() -> Text.literal("Saved SVFrameMMO data for " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int reset(ServerCommandSource source, ServerPlayerEntity player, ResetKind kind, boolean reallocate) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        switch (kind) {
            case CLASSES -> PlayerResetRuntime.resetClasses(data);
            case LEVELS -> PlayerResetRuntime.resetLevels(data);
            case SKILLS -> PlayerResetRuntime.resetSkills(data);
            case ATTRIBUTES -> PlayerResetRuntime.resetAttributes(data, reallocate);
            case SKILL_TREES -> PlayerResetRuntime.resetSkillTrees(data);
            case ALL -> PlayerResetRuntime.resetAll(data, reallocate);
        }
        SVFrameMMO.playerData().save();
        source.sendFeedback(() -> Text.literal(kind.display + " data of " + player.getName().getString() + " was successfully reset."), true);
        return 1;
    }

    private enum ResetKind {
        CLASSES("Class"), LEVELS("Main and profession level"), SKILLS("Skill"), ATTRIBUTES("Attribute"),
        SKILL_TREES("Skill tree"), ALL("Player");
        private final String display;
        ResetKind(String display) { this.display = display; }
    }
}
