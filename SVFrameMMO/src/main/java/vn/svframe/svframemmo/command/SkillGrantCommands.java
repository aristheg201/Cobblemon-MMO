package vn.svframe.svframemmo.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.skill.ClassSkill;
import vn.svframe.svframemmo.skill.gui.SkillListGui;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Admin skill grants plus the unified MMOCore-style RPG skill list entry point. */
public final class SkillGrantCommands implements ModInitializer {
    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerGrant(dispatcher, "teachskill");
            registerGrant(dispatcher, "giveskill");
            dispatcher.register(literal("mmo")
                    .then(literal("skill").executes(ctx -> openSkills(ctx.getSource())))
                    .then(literal("skills").executes(ctx -> openSkills(ctx.getSource()))));
        });
    }

    private static int openSkills(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SkillListGui.open(source.getPlayerOrThrow());
        return 1;
    }

    private static void registerGrant(CommandDispatcher<ServerCommandSource> dispatcher, String literalName) {
        dispatcher.register(literal(literalName)
                .requires(source -> source.hasPermissionLevel(2))
                .then(argument("player", EntityArgumentType.player())
                        .then(argument("skill", StringArgumentType.word())
                                .suggests(SkillGrantCommands::suggestTargetSkills)
                                .executes(ctx -> grant(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                        StringArgumentType.getString(ctx, "skill"), 1))
                                .then(argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> grant(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "skill"), IntegerArgumentType.getInteger(ctx, "level")))))));
    }

    private static int grant(ServerCommandSource source, ServerPlayerEntity player, String skillId, int requestedLevel) {
        PlayerData data = SVFrameMMO.playerData().get(player);
        ClassSkill classSkill = data.getProfess().getSkill(skillId);
        ClassSkill externalSkill = classSkill == null ? SVFrameMMO.externalSkills().get(skillId) : null;
        ClassSkill skill = classSkill != null ? classSkill : externalSkill;
        if (skill == null) {
            source.sendError(Text.literal("Unknown SVFrameMMO skill '" + skillId + "'."));
            return 0;
        }
        if (data.getLevel() < skill.getUnlockLevel()) {
            source.sendError(Text.literal(skill.getSkill().getName() + " requires level " + skill.getUnlockLevel()
                    + "; " + player.getName().getString() + " is level " + data.getLevel() + "."));
            return 0;
        }

        int level = Math.min(Math.max(1, requestedLevel), Math.max(1, skill.getMaxLevel()));
        if (externalSkill != null) {
            SVFrameMMO.externalProgression().learn(player.getUuid(), externalSkill.getSkill().getId(), level);
            SVFrameMMO.externalProgression().save();
        } else {
            String key = skill.getUnlockNamespacedKey();
            if (!skill.isUnlockedByDefault() && !data.hasUnlocked(key)) data.unlock(key);
            data.setSkillLevel(skill.getSkill(), level);
        }

        boolean external = externalSkill != null;
        source.sendFeedback(() -> Text.literal("Granted " + skill.getSkill().getName() + " Lv." + level
                + " to " + player.getName().getString() + (external ? " [integration skill]" : "") + "."), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestTargetSkills(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        try {
            PlayerData data = SVFrameMMO.playerData().get(EntityArgumentType.getPlayer(ctx, "player"));
            String remaining = builder.getRemainingLowerCase();
            Stream.concat(data.getProfess().getSkills().stream(), SVFrameMMO.externalSkills().getAll().stream())
                    .map(skill -> skill.getSkill().getId())
                    .filter(id -> id != null && id.toLowerCase(Locale.ROOT).startsWith(remaining))
                    .distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(builder::suggest);
        } catch (Exception ignored) { }
        return builder.buildFuture();
    }
}
