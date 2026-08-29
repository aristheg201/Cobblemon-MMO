package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skilltree.SkillTree;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Player-facing aliases matching MMOCore's retained RPG GUI commands plus the unified /mmo surface. */
public final class RpgGuiCommands {
    private RpgGuiCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("class").executes(ctx -> openClass(ctx.getSource())));
        dispatcher.register(literal("c").executes(ctx -> openClass(ctx.getSource())));
        dispatcher.register(literal("attributes").executes(ctx -> openAttributes(ctx.getSource())));
        dispatcher.register(literal("att").executes(ctx -> openAttributes(ctx.getSource())));
        dispatcher.register(literal("stats").executes(ctx -> openAttributes(ctx.getSource())));
        dispatcher.register(literal("skills").executes(ctx -> openSkills(ctx.getSource())));
        dispatcher.register(literal("s").executes(ctx -> openSkills(ctx.getSource())));
        dispatcher.register(literal("player").executes(ctx -> openStats(ctx.getSource())));
        dispatcher.register(literal("p").executes(ctx -> openStats(ctx.getSource())));
        dispatcher.register(literal("profile").executes(ctx -> openStats(ctx.getSource())));
        dispatcher.register(literal("skilltrees").executes(ctx -> openTrees(ctx.getSource(), null))
                .then(argument("tree", StringArgumentType.word()).suggests((ctx, builder) -> {
                    PlayerData data = data(ctx.getSource());
                    String remaining = builder.getRemainingLowerCase();
                    for (String id : data.getProfess().getSkillTreeIds())
                        if (id.toLowerCase(java.util.Locale.ROOT).startsWith(remaining)) builder.suggest(id);
                    return builder.buildFuture();
                }).executes(ctx -> openTrees(ctx.getSource(), StringArgumentType.getString(ctx, "tree")))));
        dispatcher.register(literal("st").executes(ctx -> openTrees(ctx.getSource(), null)));
        dispatcher.register(literal("trees").executes(ctx -> openTrees(ctx.getSource(), null)));
        dispatcher.register(literal("tree").executes(ctx -> openTrees(ctx.getSource(), null)));

        dispatcher.register(literal("mmo")
                .then(literal("class").executes(ctx -> openClass(ctx.getSource())))
                .then(literal("classes").executes(ctx -> openClass(ctx.getSource())))
                .then(literal("subclass").executes(ctx -> openSubclass(ctx.getSource())))
                .then(literal("subclasses").executes(ctx -> openSubclass(ctx.getSource())))
                .then(literal("attribute").executes(ctx -> openAttributes(ctx.getSource())))
                .then(literal("attributes").executes(ctx -> openAttributes(ctx.getSource())))
                .then(literal("skill").executes(ctx -> openSkills(ctx.getSource())))
                .then(literal("skills").executes(ctx -> openSkills(ctx.getSource())))
                .then(literal("stats").executes(ctx -> openStats(ctx.getSource())))
                .then(literal("profile").executes(ctx -> openStats(ctx.getSource())))
                .then(literal("skilltree").executes(ctx -> openTrees(ctx.getSource(), null))
                        .then(argument("tree", StringArgumentType.word())
                                .executes(ctx -> openTrees(ctx.getSource(), StringArgumentType.getString(ctx, "tree")))))
                .then(literal("skilltrees").executes(ctx -> openTrees(ctx.getSource(), null))));
    }

    private static int openClass(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SVFrameMMO.gui().openClassSelect(data(source)); return 1;
    }
    private static int openSubclass(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SVFrameMMO.gui().openSubclassSelect(data(source)); return 1;
    }
    private static int openAttributes(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SVFrameMMO.gui().openAttributes(data(source)); return 1;
    }
    private static int openSkills(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SVFrameMMO.gui().openSkills(data(source)); return 1;
    }
    private static int openStats(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SVFrameMMO.gui().openStats(data(source)); return 1;
    }
    private static int openTrees(ServerCommandSource source, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerData data = data(source);
        if (data.getProfess().getSkillTreeIds().isEmpty()) { message(source.getPlayerOrThrow(), "&cYour current class has no skill tree."); return 0; }
        if (id == null || id.isBlank()) { SVFrameMMO.gui().openSkillTree(data); return 1; }
        SkillTree tree = SVFrameMMO.skillTrees().get(id);
        if (tree == null || !data.getProfess().getSkillTreeIds().stream().anyMatch(own -> own.equalsIgnoreCase(tree.getId()))) {
            message(source.getPlayerOrThrow(), "&cThat skill tree is not available to your current class."); return 0;
        }
        SVFrameMMO.gui().openSkillTree(data, tree); return 1;
    }
    private static PlayerData data(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return SVFrameMMO.playerData().get(source.getPlayerOrThrow());
    }
    private static void message(ServerPlayerEntity player, String text) {
        player.sendMessage(Text.literal(SVFrameLib.inst().parseColors(text)), true);
    }
}
