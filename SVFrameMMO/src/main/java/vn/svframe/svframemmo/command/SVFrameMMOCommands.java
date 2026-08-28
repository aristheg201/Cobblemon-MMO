package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.EXPSource;
import vn.svframe.svframemmo.skill.ClassSkill;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native Brigadier command tree with MMOCore-style dynamic arguments and colored feedback. */
public final class SVFrameMMOCommands {
    private static final List<String> POINT_TYPES = List.of(
            "class", "skill", "attribute", "skill-reallocation", "attribute-reallocation", "tree-reallocation");

    private SVFrameMMOCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("svframemmo")
                .executes(ctx -> profile(ctx.getSource(), ctx.getSource().getPlayerOrThrow()))
                .then(literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> reload(ctx.getSource())))
                .then(literal("profile").executes(ctx -> profile(ctx.getSource(), ctx.getSource().getPlayerOrThrow())))
                .then(literal("skills").executes(ctx -> skills(ctx.getSource(), ctx.getSource().getPlayerOrThrow())))
                .then(skillTree())
                .then(literal("attribute")
                        .then(literal("spend").then(argument("attribute", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestValues(builder,
                                        SVFrameMMO.attributes().getAll().stream().map(attribute -> attribute.getId()).toList()))
                                .then(argument("amount", IntegerArgumentType.integer(1))
                                        .executes(ctx -> spendAttribute(ctx.getSource(), StringArgumentType.getString(ctx, "attribute"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(adminTree()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> skillTree() {
        return literal("skill")
                .then(literal("upgrade").then(argument("skill", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnSkills(ctx, builder, skill -> skill.isUpgradable() && !skill.getTrigger().isPassive()))
                        .executes(ctx -> upgrade(ctx.getSource(), StringArgumentType.getString(ctx, "skill"), true))))
                .then(literal("downgrade").then(argument("skill", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnSkills(ctx, builder, skill -> skill.isUpgradable() && !skill.getTrigger().isPassive()))
                        .executes(ctx -> upgrade(ctx.getSource(), StringArgumentType.getString(ctx, "skill"), false))))
                .then(literal("bind").then(argument("slot", IntegerArgumentType.integer(1))
                        .suggests(SVFrameMMOCommands::suggestOwnSlots)
                        .then(argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestOwnSkills(ctx, builder, skill -> !skill.isPermanent() && !skill.getTrigger().isPassive()))
                                .executes(ctx -> bind(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot"), StringArgumentType.getString(ctx, "skill"))))))
                .then(literal("unbind").then(argument("slot", IntegerArgumentType.integer(1))
                        .suggests(SVFrameMMOCommands::suggestOwnBoundSlots)
                        .executes(ctx -> unbind(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(literal("cast").then(argument("skill", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggestOwnSkills(ctx, builder, skill -> !skill.getTrigger().isPassive()))
                        .executes(ctx -> cast(ctx.getSource(), StringArgumentType.getString(ctx, "skill")))))
                .then(literal("castslot").then(argument("slot", IntegerArgumentType.integer(1))
                        .suggests(SVFrameMMOCommands::suggestOwnBoundSlots)
                        .executes(ctx -> castSlot(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "slot")))))
                .then(literal("unlock").requires(source -> source.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("skill", StringArgumentType.word())
                                        .suggests(SVFrameMMOCommands::suggestTargetSkills)
                                        .executes(ctx -> setSkillUnlocked(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "skill"), true)))))
                .then(literal("lock").requires(source -> source.hasPermissionLevel(2))
                        .then(argument("player", EntityArgumentType.player())
                                .then(argument("skill", StringArgumentType.word())
                                        .suggests(SVFrameMMOCommands::suggestTargetSkills)
                                        .executes(ctx -> setSkillUnlocked(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "skill"), false)))))
                .then(literal("level").requires(source -> source.hasPermissionLevel(2))
                        .then(literal("give").then(skillLevelArguments(SkillLevelOperation.GIVE)))
                        .then(literal("set").then(skillLevelArguments(SkillLevelOperation.SET)))
                        .then(literal("take").then(skillLevelArguments(SkillLevelOperation.TAKE))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, ?> skillLevelArguments(SkillLevelOperation operation) {
        return argument("player", EntityArgumentType.player())
                .then(argument("skill", StringArgumentType.word())
                        .suggests(SVFrameMMOCommands::suggestTargetSkills)
                        .then(argument("level", IntegerArgumentType.integer(1))
                                .executes(ctx -> editSkillLevel(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "skill"), IntegerArgumentType.getInteger(ctx, "level"), operation))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> adminTree() {
        return literal("admin").requires(source -> source.hasPermissionLevel(2))
                .then(literal("reload").executes(ctx -> reload(ctx.getSource())))
                .then(literal("class").then(argument("player", EntityArgumentType.player())
                        .then(argument("class", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestValues(builder,
                                        SVFrameMMO.classes().getAll().stream().map(playerClass -> playerClass.getId()).toList()))
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
                                .suggests((ctx, builder) -> suggestValues(builder, POINT_TYPES))
                                .then(argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> points(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "amount")))))))
                .then(literal("profession").then(argument("player", EntityArgumentType.player())
                        .then(argument("profession", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestValues(builder,
                                        SVFrameMMO.professions().getAll().stream().map(profession -> profession.getId()).toList()))
                                .then(argument("amount", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> professionExp(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "profession"), DoubleArgumentType.getDouble(ctx, "amount")))))))
                .then(literal("treepoints").then(argument("player", EntityArgumentType.player())
                        .then(argument("tree", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestValues(builder,
                                        SVFrameMMO.skillTrees().getAll().stream().map(tree -> tree.getId()).toList()))
                                .then(argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> treePoints(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "tree"), IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    private static CompletableFuture<Suggestions> suggestOwnSkills(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder,
                                                                    Predicate<ClassSkill> predicate) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(ctx.getSource().getPlayerOrThrow());
            return suggestValues(builder, configuredSkillIds(data, predicate));
        } catch (Exception ignored) { return builder.buildFuture(); }
    }

    private static CompletableFuture<Suggestions> suggestTargetSkills(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(EntityArgumentType.getPlayer(ctx, "player"));
            return suggestValues(builder, configuredSkillIds(data, skill -> true));
        } catch (Exception ignored) { return builder.buildFuture(); }
    }

    private static List<String> configuredSkillIds(PlayerData data, Predicate<ClassSkill> predicate) {
        Set<String> configured = new LinkedHashSet<>();
        Object raw = data.getProfess().getRawConfig().get("skills");
        if (raw instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                String id = UtilityMethods.enumName(String.valueOf(key));
                ClassSkill skill = data.getProfess().getSkill(id);
                if (skill != null && predicate.test(skill)) configured.add(skill.getSkill().getId());
            }
        }
        if (configured.isEmpty())
            data.getProfess().getSkills().stream().filter(predicate).map(skill -> skill.getSkill().getId()).forEach(configured::add);
        return configured.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private static CompletableFuture<Suggestions> suggestOwnSlots(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(ctx.getSource().getPlayerOrThrow());
            return suggestValues(builder, data.getProfess().getSlots().stream().map(slot -> Integer.toString(slot.slot())).toList());
        } catch (Exception ignored) { return builder.buildFuture(); }
    }

    private static CompletableFuture<Suggestions> suggestOwnBoundSlots(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(ctx.getSource().getPlayerOrThrow());
            return suggestValues(builder, data.getSkillBindings().keySet().stream().sorted().map(String::valueOf).toList());
        } catch (Exception ignored) { return builder.buildFuture(); }
    }

    private static CompletableFuture<Suggestions> suggestValues(SuggestionsBuilder builder, Collection<String> values) {
        String remaining = builder.getRemainingLowerCase();
        values.stream().filter(value -> value != null && value.toLowerCase(Locale.ROOT).startsWith(remaining))
                .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static int reload(ServerCommandSource source) {
        String version = FabricLoader.getInstance().getModContainer(SVFrameMMO.ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        verbose(source, "&eReloading SVFrameMMO " + version + "...");
        long started = System.currentTimeMillis();
        boolean ok = SVFrameMMO.reload();
        long elapsed = Math.max(0L, System.currentTimeMillis() - started);
        if (!ok) { error(source, "Reload failed. Check the server log for the invalid configuration entry."); return 0; }
        verbose(source, "&eSVFrameMMO " + version + " successfully reloaded.");
        verbose(source, "&eTime Taken: &6" + elapsed + "&ems");
        return 1;
    }

    private static int profile(ServerCommandSource source, ServerPlayerEntity player) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        success(source, "&eClass: &6" + data.getClassId() + " &eLv.&6" + data.getLevel()
                + " &eEXP: &6" + trim(data.getExperience()) + "&e/&6" + data.getLevelUpExperience()
                + " &e| Skill Points: &6" + data.getSkillPoints() + " &e| Attribute Points: &6" + data.getAttributePoints()
                + " &e| Mana: &6" + trim(data.getMana()) + " &e| Stamina: &6" + trim(data.getStamina())
                + " &e| Stellium: &6" + trim(data.getStellium()));
        return 1;
    }

    private static int skills(ServerCommandSource source, ServerPlayerEntity player) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        String text = configuredSkillIds(data, data::canUseSkill).stream()
                .map(id -> id + "@" + data.getSkillLevel(id)).collect(Collectors.joining("&7, &6"));
        success(source, text.isBlank() ? "&cNo unlocked skills." : "&eSkills: &6" + text);
        return 1;
    }

    private static int upgrade(ServerCommandSource source, String skill, boolean up) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
        boolean changed = up ? data.upgradeSkill(skill) : data.downgradeSkill(skill);
        if (!changed) { error(source, "Skill progression request was rejected."); return 0; }
        success(source, "&e" + (up ? "Upgraded " : "Downgraded ") + "&6" + skill + "&e to level &6" + data.getSkillLevel(skill));
        return 1;
    }

    private static int bind(ServerCommandSource source, int slot, String skill) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
            data.bindSkill(slot, skill);
            success(source, "&eSkill &6" + UtilityMethods.enumName(skill) + "&e now bound to slot &6" + slot);
            return 1;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static int unbind(ServerCommandSource source, int slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
        String removed = data.unbindSkill(slot);
        if (removed == null) { error(source, "Could not find skill at slot &6" + slot); return 0; }
        success(source, "&eSkill &6" + removed + "&e successfully unbound from slot &6" + slot);
        return 1;
    }

    private static int cast(ServerCommandSource source, String skill) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            var result = SVFrameMMO.skillRuntime().cast(SVFrameMMO.playerData().get(source.getPlayerOrThrow()), skill);
            return result.isSuccessful() ? 1 : 0;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static int castSlot(ServerCommandSource source, int slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
            var result = SVFrameMMO.skillRuntime().castBound(data, slot);
            return result.isSuccessful() ? 1 : 0;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static int setSkillUnlocked(ServerCommandSource source, ServerPlayerEntity player, String id, boolean unlock) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        ClassSkill skill = data.getProfess().getSkill(id);
        if (skill == null || !configuredSkillIds(data, candidate -> true).contains(skill.getSkill().getId())) {
            error(source, "Class " + data.getProfess().getName() + " doesn't have a skill called '" + id + "'.");
            return 0;
        }
        String key = skill.getUnlockNamespacedKey();
        boolean already = data.hasUnlocked(key);
        if (unlock && already) { error(source, "Skill " + skill.getSkill().getName() + " already unlocked for " + player.getName().getString()); return 0; }
        if (!unlock && !already) { error(source, "Skill " + skill.getSkill().getName() + " already locked for " + player.getName().getString()); return 0; }
        boolean changed = unlock ? data.unlock(key) : data.lock(key);
        if (!changed && unlock && skill.isUnlockedByDefault()) {
            error(source, "Skill " + skill.getSkill().getName() + " is unlocked by default and cannot be unlocked again.");
            return 0;
        }
        success(source, "&eSkill &6" + skill.getSkill().getName() + "&e " + (unlock ? "unlocked" : "locked") + " for &6" + player.getName().getString());
        return 1;
    }

    private static int editSkillLevel(ServerCommandSource source, ServerPlayerEntity player, String id, int amount, SkillLevelOperation operation) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(player);
            ClassSkill skill = data.getProfess().getSkill(id);
            if (skill == null || !configuredSkillIds(data, candidate -> true).contains(skill.getSkill().getId())) {
                error(source, id + " is not unlockable for " + player.getName().getString() + ".");
                return 0;
            }
            if (skill.getUnlockLevel() > data.getLevel()) {
                error(source, skill.getSkill().getName() + " is not unlockable for " + player.getName().getString() + ".");
                return 0;
            }
            int current = data.getSkillLevel(skill.getSkill());
            int next = switch (operation) {
                case GIVE -> current + amount;
                case SET -> amount;
                case TAKE -> current - amount;
            };
            data.setSkillLevel(skill.getSkill(), Math.max(1, next));
            success(source, "&6" + player.getName().getString() + "&e is now level &6" + data.getSkillLevel(skill.getSkill())
                    + "&e for " + skill.getSkill().getName());
            return 1;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static int spendAttribute(ServerCommandSource source, String attribute, int amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            PlayerData data = SVFrameMMO.playerData().get(source.getPlayerOrThrow());
            if (!data.spendAttributePoints(attribute, amount)) { error(source, "No attribute points were spent."); return 0; }
            success(source, "&e" + attribute + "=&6" + data.getAttributes().getAttribute(attribute) + " &e| points=&6" + data.getAttributePoints()); return 1;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static int setClass(ServerCommandSource source, ServerPlayerEntity player, String id) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(player);
            boolean changed = data.changeClass(SVFrameMMO.classes().getOrThrow(id), PlayerClassChangeEvent.Reason.COMMAND_FORCE);
            if (!changed) return 0;
            success(source, "&eClass of player &6" + player.getName().getString() + "&e forcefully set to &6" + data.getProfess().getName()); return 1;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static int giveExp(ServerCommandSource source, ServerPlayerEntity player, double amount) {
        SVFrameMMO.playerData().get(player).giveExperience(amount, EXPSource.COMMAND);
        success(source, "&eAdjusted &6" + player.getName().getString() + "&e EXP by &6" + trim(amount)); return 1;
    }

    private static int setExp(ServerCommandSource source, ServerPlayerEntity player, double amount) {
        SVFrameMMO.playerData().get(player).setExperience(amount);
        success(source, "&eSet &6" + player.getName().getString() + "&e EXP to &6" + trim(amount)); return 1;
    }

    private static int points(ServerCommandSource source, ServerPlayerEntity player, String type, int amount) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        switch (type.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "class" -> data.giveClassPoints(amount);
            case "skill" -> data.giveSkillPoints(amount);
            case "attribute" -> data.giveAttributePoints(amount);
            case "skill-reallocation" -> data.giveSkillReallocationPoints(amount);
            case "attribute-reallocation" -> data.giveAttributeReallocationPoints(amount);
            case "tree-reallocation" -> data.giveSkillTreeReallocationPoints(amount);
            default -> { error(source, "Unknown point type: " + type); return 0; }
        }
        success(source, "&eAdjusted &6" + type + "&e points for &6" + player.getName().getString() + "&e by &6" + amount); return 1;
    }

    private static int professionExp(ServerCommandSource source, ServerPlayerEntity player, String id, double amount) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(player);
            data.getProfessions().giveExperience(SVFrameMMO.professions().getOrThrow(id), amount, EXPSource.COMMAND);
            success(source, "&eAdjusted profession &6" + id + "&e EXP by &6" + trim(amount)); return 1;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static int treePoints(ServerCommandSource source, ServerPlayerEntity player, String tree, int amount) {
        try {
            SVFrameMMO.skillTrees().getOrThrow(tree);
            PlayerData data = SVFrameMMO.playerData().get(player);
            data.getSkillTrees().givePoints(tree, amount);
            success(source, "&eTree &6" + tree + "&e points=&6" + data.getSkillTrees().getPoints(tree)); return 1;
        } catch (RuntimeException exception) { error(source, exception.getMessage()); return 0; }
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static void verbose(ServerCommandSource source, String message) { success(source, message); }
    private static void success(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(SVFrameLib.inst().parseColors(message == null ? "" : message)), false);
    }
    private static void error(ServerCommandSource source, String message) {
        source.sendError(Text.literal(SVFrameLib.inst().parseColors("&c" + (message == null ? "Unknown error" : message))));
    }

    private enum SkillLevelOperation { GIVE, SET, TAKE }
}
