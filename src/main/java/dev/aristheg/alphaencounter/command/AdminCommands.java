package dev.aristheg.alphaencounter.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.integration.RankScalingService;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.Map;

public final class AdminCommands {
    private AdminCommands() {}

    public static void register(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("alphaencounter").requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.literal("help").executes(context -> help(context.getSource())))
            .then(CommandManager.literal("reload").executes(context -> {
                AlphaEncounterMod.RUNTIME.reloadConfig();
                feedback(context.getSource(), "admin.reload.success");
                return 1;
            }))
            .then(CommandManager.literal("save").executes(context -> {
                AlphaEncounterMod.RUNTIME.saveState(context.getSource().getServer());
                feedback(context.getSource(), "admin.save.success");
                return 1;
            }))
            .then(CommandManager.literal("debug")
                .executes(context -> {
                    feedback(
                        context.getSource(),
                        "admin.debug.runtime",
                        Map.of("arg_details", AlphaEncounterMod.RUNTIME.perfLine())
                    );
                    return 1;
                })
                .then(CommandManager.literal("reset").executes(context -> {
                    AlphaEncounterMod.RUNTIME.resetCounters();
                    feedback(context.getSource(), "admin.debug.reset");
                    return 1;
                }))
                .then(CommandManager.literal("player")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> debugPlayer(
                            context.getSource(),
                            EntityArgumentType.getPlayer(context, "player")
                        )))))
            .then(CommandManager.literal("list").executes(context -> list(context.getSource())))
            .then(CommandManager.literal("spawn")
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        AlphaEncounterMod.CONFIG.encounterIds().forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(context -> spawn(
                        context.getSource(),
                        StringArgumentType.getString(context, "id"),
                        null
                    ))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> spawn(
                            context.getSource(),
                            StringArgumentType.getString(context, "id"),
                            EntityArgumentType.getPlayer(context, "player")
                        )))))
            .then(CommandManager.literal("inspect")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .executes(context -> inspect(
                        context.getSource(),
                        StringArgumentType.getString(context, "target")
                    ))))
            .then(CommandManager.literal("despawn")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .executes(context -> despawn(
                        context.getSource(),
                        StringArgumentType.getString(context, "target")
                    ))))
            .then(CommandManager.literal("defeat")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .executes(context -> defeat(
                        context.getSource(),
                        StringArgumentType.getString(context, "target")
                    ))))
            .then(CommandManager.literal("sethp")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .then(CommandManager.argument("percent", FloatArgumentType.floatArg(1f, 100f))
                        .executes(context -> setHp(
                            context.getSource(),
                            StringArgumentType.getString(context, "target"),
                            FloatArgumentType.getFloat(context, "percent")
                        )))))
            .then(CommandManager.literal("battle")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> battle(
                            context.getSource(),
                            StringArgumentType.getString(context, "target"),
                            EntityArgumentType.getPlayer(context, "player")
                        )))))
            .then(CommandManager.literal("attack")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .executes(context -> attack(
                            context.getSource(),
                            StringArgumentType.getString(context, "target"),
                            EntityArgumentType.getPlayer(context, "player")
                        )))))
            .then(CommandManager.literal("anim")
                .then(CommandManager.argument("target", StringArgumentType.word())
                    .then(CommandManager.argument("animation", StringArgumentType.word())
                        .executes(context -> anim(
                            context.getSource(),
                            StringArgumentType.getString(context, "target"),
                            StringArgumentType.getString(context, "animation")
                        )))))
            .then(CommandManager.literal("reset")
                .then(CommandManager.argument("id", StringArgumentType.word())
                    .executes(context -> {
                        String id = StringArgumentType.getString(context, "id");
                        AlphaEncounterMod.RUNTIME.resetCooldown(id);
                        feedback(context.getSource(), "admin.cooldown.reset", Map.of("arg_id", id));
                        return 1;
                    }))));
    }

    private static int help(ServerCommandSource source) {
        feedback(source, "admin.help");
        return 1;
    }

    private static int debugPlayer(ServerCommandSource source, ServerPlayerEntity player) {
        RankScalingService.Resolution resolved = RankScalingService.instance().resolve(player);
        String matched = resolved.matchedPermission().isBlank()
            ? AlphaEncounterMod.UI.text("admin.rank.fallback")
            : resolved.matchedPermission() + "@" + resolved.priority();
        feedback(source, "admin.debug.rank", Map.of(
            "arg_player", player.getName().getString(),
            "arg_enabled", Boolean.toString(resolved.enabled()),
            "arg_provider", resolved.providerStatus(),
            "arg_provider_available", Boolean.toString(resolved.providerAvailable()),
            "arg_profile", resolved.profileId(),
            "arg_matched", matched,
            "arg_spawn_multiplier", formatMultiplier(resolved.spawnChanceMultiplier())
        ));
        return 1;
    }

    private static int list(ServerCommandSource source) {
        var active = AlphaEncounterMod.RUNTIME.activeSorted();
        feedback(source, "admin.list.header", Map.of("arg_count", Integer.toString(active.size())));
        for (ActiveEncounter encounter : active) {
            feedbackEncounter(source, "admin.inspect", encounter);
        }
        return active.size();
    }

    private static int spawn(ServerCommandSource source, String id, ServerPlayerEntity player) {
        ServerWorld world = player == null ? source.getWorld() : player.getServerWorld();
        BlockPos pos = player == null ? BlockPos.ofFloored(source.getPosition()) : player.getBlockPos();
        ActiveEncounter encounter = AlphaEncounterMod.RUNTIME.spawnEncounter(id, world, pos, true);
        if (encounter == null) {
            feedback(source, "admin.spawn.failed", Map.of("arg_id", id));
            return 0;
        }
        feedbackEncounter(source, "admin.inspect", encounter);
        return 1;
    }

    private static int inspect(ServerCommandSource source, String target) {
        ActiveEncounter encounter = AlphaEncounterMod.RUNTIME.resolveAdminTarget(source, target);
        if (encounter == null) {
            feedback(source, "admin.target.not_found");
            return 0;
        }
        feedbackEncounter(source, "admin.inspect", encounter);
        return 1;
    }

    private static int despawn(ServerCommandSource source, String target) {
        boolean ok = AlphaEncounterMod.RUNTIME.adminDespawn(AlphaEncounterMod.RUNTIME.resolveAdminTarget(source, target));
        feedback(source, ok ? "admin.despawn.success" : "admin.target.not_found");
        return ok ? 1 : 0;
    }

    private static int defeat(ServerCommandSource source, String target) {
        boolean ok = AlphaEncounterMod.RUNTIME.adminDefeat(
            source.getServer(),
            AlphaEncounterMod.RUNTIME.resolveAdminTarget(source, target)
        );
        feedback(source, ok ? "admin.defeat.success" : "admin.target.not_found");
        return ok ? 1 : 0;
    }

    private static int setHp(ServerCommandSource source, String target, float percent) {
        boolean ok = AlphaEncounterMod.RUNTIME.adminSetHp(
            source.getServer(),
            AlphaEncounterMod.RUNTIME.resolveAdminTarget(source, target),
            percent
        );
        if (ok) {
            feedback(source, "admin.sethp.success", Map.of("arg_percent", formatNumber(percent)));
        } else {
            feedback(source, "admin.target.not_found");
        }
        return ok ? 1 : 0;
    }

    private static int battle(ServerCommandSource source, String target, ServerPlayerEntity player) {
        boolean ok = AlphaEncounterMod.RUNTIME.adminBattle(
            AlphaEncounterMod.RUNTIME.resolveAdminTarget(source, target),
            player
        );
        feedback(source, ok ? "admin.battle.success" : "admin.battle.failed");
        return ok ? 1 : 0;
    }

    private static int attack(ServerCommandSource source, String target, ServerPlayerEntity player) {
        boolean ok = AlphaEncounterMod.RUNTIME.adminAttack(
            source.getServer(),
            AlphaEncounterMod.RUNTIME.resolveAdminTarget(source, target),
            player
        );
        feedback(source, ok ? "admin.attack.success" : "admin.attack.failed");
        return ok ? 1 : 0;
    }

    private static int anim(ServerCommandSource source, String target, String animation) {
        boolean ok = AlphaEncounterMod.RUNTIME.adminAnimation(
            source.getServer(),
            AlphaEncounterMod.RUNTIME.resolveAdminTarget(source, target),
            animation
        );
        if (ok) {
            feedback(source, "admin.animation.success", Map.of("arg_animation", animation));
        } else {
            feedback(source, "admin.animation.failed");
        }
        return ok ? 1 : 0;
    }

    private static void feedbackEncounter(ServerCommandSource source, String key, ActiveEncounter encounter) {
        source.sendFeedback(
            () -> AlphaEncounterMod.TEXT.message(
                key,
                AlphaEncounterMod.RUNTIME.textContext(source.getServer(), encounter, null)
            ),
            false
        );
    }

    private static void feedback(ServerCommandSource source, String key) {
        feedback(source, key, Map.of());
    }

    private static void feedback(ServerCommandSource source, String key, Map<String, String> arguments) {
        source.sendFeedback(() -> AlphaEncounterMod.TEXT.message(key, null, arguments), false);
    }

    private static String formatMultiplier(double value) {
        if (value == Math.rint(value)) return String.format(Locale.ROOT, "%.1f", value);
        return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatNumber(float value) {
        if (value == Math.rint(value)) return Integer.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
