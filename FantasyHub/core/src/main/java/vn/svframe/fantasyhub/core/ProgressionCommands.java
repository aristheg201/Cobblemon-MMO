package vn.svframe.fantasyhub.core;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import com.google.gson.Gson;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.LinkedHashMap;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;

/** Ordinary players can exercise the same server transactions as the native GUI. */
final class ProgressionCommands {
  static void install() {
    CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
      var root = literal("fantasyprogress")
          .then(literal("status").executes(ctx -> {
            var player = ctx.getSource().getPlayerOrThrow();
            ctx.getSource().sendFeedback(() -> Text.literal(status(player)), false); return 1;
          }))
          .then(literal("unlock").then(argument("class", StringArgumentType.word()).executes(ctx ->
              ClassProgression.purchase(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "class")) ? 1 : 0)))
          .then(literal("choose").then(argument("class", StringArgumentType.word()).executes(ctx ->
              ClassSwitchService.request(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "class"), () -> {}) ? 1 : 0)))
          .then(literal("learn").then(argument("skill", StringArgumentType.word()).executes(ctx ->
              ClassProgression.learn(ctx.getSource().getPlayerOrThrow(), StringArgumentType.getString(ctx, "skill")) ? 1 : 0)))
          .then(literal("reload").requires(source -> source.hasPermissionLevel(2)).executes(ctx -> {
            try { ClassProgression.reload(); ctx.getSource().sendFeedback(() -> Text.literal("Đã nạp FantasyHub/progression.json."), true); return 1; }
            catch (Exception failure) { ctx.getSource().sendError(Text.literal(failure.getMessage())); return 0; }
          }))
          .then(literal("sigils").requires(source -> source.hasPermissionLevel(2))
              .then(argument("player", EntityArgumentType.player()).then(argument("amount", IntegerArgumentType.integer(1, 1_000_000)).executes(ctx -> {
                ClassProgression.awardSigils(EntityArgumentType.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "amount")); return 1;
              }))));
      for (String operation : new String[]{"grant", "revoke", "starter-reset", "starter-revoke", "recovery-status", "recovery-refund", "recovery-dismiss"}) {
        var target = argument("player", EntityArgumentType.player());
        if (operation.equals("grant") || operation.equals("revoke"))
          target.then(argument("class", StringArgumentType.word()).executes(ctx -> {
            var player = EntityArgumentType.getPlayer(ctx, "player");
            ClassProgression.changeOwnership(SVFrameMMO.playerData().get(player), StringArgumentType.getString(ctx, "class").toUpperCase(Locale.ROOT), operation.equals("grant")); return 1;
          }));
        else target.executes(ctx -> {
          var player = EntityArgumentType.getPlayer(ctx, "player");
          try {
            if (operation.startsWith("starter-")) ClassProgression.resetStarter(SVFrameMMO.playerData().get(player), operation.equals("starter-reset"));
            else if (operation.equals("recovery-status")) ctx.getSource().sendFeedback(() -> Text.literal(recovery(player)), false);
            else ClassSwitchService.reconcile(player, operation.equals("recovery-refund"));
            return 1;
          } catch (Exception failure) { ctx.getSource().sendError(Text.literal(failure.getMessage())); return 0; }
        });
        root.then(literal(operation).requires(source -> source.hasPermissionLevel(2)).then(target));
      }
      dispatcher.register(root);
    });
  }
  private static String recovery(ServerPlayerEntity player) {
    try { return ClassSwitchService.recoveryStatus(player); }
    catch (Exception failure) { return failure.toString(); }
  }
  private static String status(ServerPlayerEntity player) {
    var data = SVFrameMMO.playerData().get(player);
    var out = new LinkedHashMap<String, Object>(ClassSwitchService.policy(data));
    out.put("class", data.getClassId()); out.put("level", data.getLevel()); out.put("skillPoints", data.getSkillPoints());
    out.put("learnCost", ClassProgression.settings().learnCost());
    out.put("owned", SVFrameMMO.classes().getAll().stream().filter(type -> ClassProgression.owns(data, type.getId())).map(type -> type.getId()).sorted().toList());
    out.put("learned", data.getUnlockedItems().stream().filter(key -> key.startsWith("skill:")).sorted().toList());
    out.put("lastTransaction", data.getPreference("fantasyhub:last-transaction", ""));
    out.put("recovery", recovery(player));
    return new Gson().toJson(out);
  }
}
