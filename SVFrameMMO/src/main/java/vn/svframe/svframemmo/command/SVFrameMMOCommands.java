package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.EXPSource;

import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Useful native Brigadier surface for player progression and server administration. */
public final class SVFrameMMOCommands {
    private SVFrameMMOCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("svframemmo")
                .executes(ctx -> profile(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
                .then(literal("profile").executes(ctx -> profile(ctx.getSource(), ctx.getSource().getPlayerOrThrow())))
                .then(literal("skills").executes(ctx -> skills(ctx.getSource(), ctx.getSource().getPlayerOrThrow())))
                .then(literal("skill")
                        .then(literal("upgrade").then(argument("skill", StringArgumentType.word())
                                .executes(ctx -> upgrade(ctx.getSource(), StringArgumentType.getString(ctx, "skill"), true))))
                        .then(literal("downgrade").then(argument("skill", StringArgumentType.word())
                                .executes(ctx -> upgrade(ctx.getSource(), StringArgumentType.getString(ctx, "skill"), false))))
                        .then(literal("bind").then(argument("slot", IntegerArgumentType.integer(1))
                                .then(argument("skill", StringArgumentType.word())
                                        .executes(ctx -> bind(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), StringArgumentType.getString(ctx, "skill"))))))
                        .then(literal("unbind").then(argument("slot", IntegerArgumentType.integer(1))
                                .executes(ctx -> unbind(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                        .then(literal("cast").then(argument("skill", StringArgumentType.word())
                                .executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "skill")))))
                        .then(literal("castslot").then(argument("slot", IntegerArgumentType.integer(1))
                                .executes(ctx -> castSlot(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"))))))
                .then(literal("attribute")
                        .then(literal("spend").then(argument("attribute", StringArgumentType.word())
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> spendAttribute(ctx.getSource(), StringArgumentType.getString(ctx, "attribute"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(adminTree()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminTree() {
        return literal("admin").requires(source -> source.hasPermissionLevel(2))
                .then(literal("reload").executes(ctx -> {
                    boolean ok = SVFrameMMO.reload();
                    if (!ok) { ctx.getSource().sendError(Text.literal("SVFrameMMO reload failed; check server log.")); return 0; }
                    success(ctx.getSource(), "SVFrameMMO reloaded | " + SVFrameMMO.definitionSummary());
                    return 1;
                }))
                .then(literal("class").then(argument("player", EntityArgumentType.player())
                        .then(argument("class", StringArgumentType.word())
                                .executes(ctx -> setClass(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "class"))))))
                .then(literal("exp")
                        .then(literal("give").then(argument("player", EntityArgumentType.player())
                                .then(argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> giveExp(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "amount"))))))
                        .then(literal("set").then(argument("player", EntityArgumentType.player())
                                .then(argument("amount", DoubleArgumentType.doubleArg(0d))
                                        .executes(ctx -> setExp(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), DoubleArgumentType.getDouble(ctx, "amount")))))))
                .then(literal("points").then(argument("player", EntityArgumentType.player())
                        .then(argument("type", StringArgumentType.word())
                                .then(argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> points(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(literal("profession").then(argument("player", EntityArgumentType.player())
                        .then(argument("profession", StringArgumentType.word())
                                .then(argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> professionExp(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "profession"), DoubleArgumentType.getDouble(ctx, "amount")))))))
                .then(literal("treepoints").then(argument("player", EntityArgumentType.player())
                        .then(argument("tree", StringArgumentType.word())
                                .then(argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> treePoints(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tree"), IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    private static int profile(ServerCommandSource source, ServerPlayerEntity player) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        source.sendFeedback(() -> Text.literal("Class=" + data.getClassId() + " Lv." + data.getLevel()
                + " EXP=" + trim(data.getExperience()) + "/" + data.getLevelUpExperience()
                + " | skill=" + data.getSkillPoints() + " attribute=" + data.getAttributePoints()
                + " | mana=" + trim(data.getMana()) + " stamina=" + trim(data.getStamina()) + " stellium=" + trim(data.getStellium())), false);
        return 1;
    }

    private static int skills(ServerCommandSource source, ServerPlayerEntity player) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        String text = data.getProfess().getSkills().stream()
                .filter(data::canUseSkill)
                .map(skill -> skill.getSkill().getId() + "@" + data.getSkillLevel(skill.getSkill()))
                .collect(Collectors.joining(", "));
        success(source, text.isBlank() ? "No unlocked skills." : text);
        return 1;
    }

    private static int upgrade(ServerCommandSource source, String skill, boolean up) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
        boolean changed = up ? data.upgradeSkill(skill) : data.downgradeSkill(skill);
        if (!changed) { source.sendError(Text.literal("Skill progression request was rejected.")); return 0; }
        success(source, (up ? "Upgraded " : "Downgraded ") + skill + " to level " + data.getSkillLevel(skill));
        return 1;
    }

    private static int bind(ServerCommandSource source, int slot, String skill) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
            data.bindSkill(slot, skill); success(source, "Bound " + skill + " to slot " + slot); return 1;
        } catch (RuntimeException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static int unbind(ServerCommandSource source, int slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
        String removed = data.unbindSkill(slot);
        if (removed == null) { source.sendError(Text.literal("No skill bound to slot " + slot)); return 0; }
        success(source, "Unbound " + removed + " from slot " + slot); return 1;
    }

    private static int cast(ServerCommandSource source, String skill) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var result = SVFrameMMO.skillRuntime().cast(SVFrameMMO.playerData().get(source.getPlayerOrThrow()), skill);
            return result.isSuccessful() ? 1 : 0;
        } catch (RuntimeException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static int castSlot(ServerCommandSource source, int slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
            var result = SVFrameMMO.skillRuntime().castBound(data, slot);
            return result.isSuccessful() ? 1 : 0;
        } catch (RuntimeException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static int spendAttribute(ServerCommandSource source, String attribute, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
            if (!data.spendAttributePoints(attribute, amount)) { source.sendError(Text.literal("No attribute points were spent.")); return 0; }
            success(source, attribute + "=" + data.getAttributes().getAttribute(attribute) + " | points=" + data.getAttributePoints()); return 1;
        } catch (RuntimeException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static int setClass(ServerCommandSource source, ServerPlayerEntity player, String id) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(player);
            boolean changed = data.changeClass(SVFrameMMO.classes().getOrThrow(id), PlayerClassChangeEvent.Reason.COMMAND_FORCE);
            if (!changed) return 0;
            success(source, "Set " + player.getGameProfile().getName() + " class to " + data.getClassId()); return 1;
        } catch (RuntimeException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static int giveExp(ServerCommandSource source, ServerPlayerEntity player, double amount) {
        SVFrameMMO.playerData().get(player).giveExperience(amount, EXPSource.COMMAND);
        success(source, "Adjusted " + player.getGameProfile().getName() + " EXP by " + trim(amount)); return 1;
    }

    private static int setExp(ServerCommandSource source, ServerPlayerEntity player, double amount) {
        SVFrameMMO.playerData().get(player).setExperience(amount);
        success(source, "Set " + player.getGameProfile().getName() + " EXP to " + trim(amount)); return 1;
    }

    private static int points(ServerCommandSource source, ServerPlayerEntity player, String type, int amount) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        switch (type.toLowerCase(java.util.Locale.ROOT).replace('_', '-')) {
            case "class" -> data.giveClassPoints(amount);
            case "skill" -> data.giveSkillPoints(amount);
            case "attribute" -> data.giveAttributePoints(amount);
            case "skill-reallocation" -> data.giveSkillReallocationPoints(amount);
            case "attribute-reallocation" -> data.giveAttributeReallocationPoints(amount);
            case "tree-reallocation" -> data.giveSkillTreeReallocationPoints(amount);
            default -> { source.sendError(Text.literal("Unknown point type: " + type)); return 0; }
        }
        success(source, "Adjusted " + type + " points for " + player.getGameProfile().getName() + " by " + amount); return 1;
    }

    private static int professionExp(ServerCommandSource source, ServerPlayerEntity player, String id, double amount) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(player);
            data.getProfessions().giveExperience(SVFrameMMO.professions().getOrThrow(id), amount, EXPSource.COMMAND);
            success(source, "Adjusted profession " + id + " EXP by " + trim(amount)); return 1;
        } catch (RuntimeException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static int treePoints(ServerCommandSource source, ServerPlayerEntity player, String tree, int amount) {
        try {
            SVFrameMMO.skillTrees().getOrThrow(tree);
            PlayerData data = SVFrameMMO.playerData().get(player);
            data.getSkillTrees().givePoints(tree, amount);
            success(source, "Tree " + tree + " points=" + data.getSkillTrees().getPoints(tree)); return 1;
        } catch (RuntimeException exception) { source.sendError(Text.literal(exception.getMessage())); return 0; }
    }

    private static String trim(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.2f", value); }
    private static void success(ServerCommandSource source, String message) { source.sendFeedback(() -> Text.literal(message), false); }
}
