package vn.svframe.svframemmo.cobblemon.fusion;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.cobblemon.integration.LuckPermsIntegration;

/** /fusion is Fusion Dance only. Potara remains item + deployed-Pokemon interaction. */
public final class FusionCommands {
    private FusionCommands() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, FusionService fusions) {
        FusionPartyGui gui = new FusionPartyGui(fusions);
        dispatcher.register(CommandManager.literal("fusion").executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            if (!LuckPermsIntegration.has(player, LuckPermsIntegration.DANCE)) {
                player.sendMessage(Text.literal("You do not have permission to use Fusion Dance."), true);
                return 0;
            }
            FusionSession active = fusions.session(player.getUuid());
            if (active != null) {
                player.sendMessage(Text.literal(active.dance() ? "Fusion Dance is already active." : "Unfuse your Potara fusion before using Fusion Dance."), true);
                return 0;
            }
            long cooldown = fusions.cooldowns().danceRemainingMillis(player.getUuid());
            if (cooldown > 0L) {
                player.sendMessage(Text.literal("Fusion Dance cooldown: " + formatSeconds(cooldown) + "."), true);
                return 0;
            }
            gui.open(player);
            return 1;
        }));

        dispatcher.register(CommandManager.literal("unfuse").executes(context -> {
            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
            if (!LuckPermsIntegration.has(player, LuckPermsIntegration.UNFUSE)) {
                player.sendMessage(Text.literal("You do not have permission to unfuse."), true);
                return 0;
            }
            FusionSession active = fusions.session(player.getUuid());
            if (active == null) { player.sendMessage(Text.literal("You are not fused."), true); return 0; }
            if (active.dance()) { player.sendMessage(Text.literal("Fusion Dance cannot be manually unfused."), true); return 0; }
            long cooldown = fusions.cooldowns().potaraRemainingMillis(player.getUuid());
            if (cooldown > 0L) { player.sendMessage(Text.literal("Potara action cooldown: " + formatSeconds(cooldown) + "."), true); return 0; }
            FusionService.EndResult result = fusions.end(player, true);
            if (!result.success()) { player.sendMessage(Text.literal(result.rejection()), true); return 0; }
            fusions.cooldowns().markPotara(player.getUuid(), fusions.potaraCooldownSeconds());
            player.sendMessage(Text.literal("Potara fusion ended."), true);
            return 1;
        }));
    }

    static String formatSeconds(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes > 0L ? minutes + "m " + remainder + "s" : seconds + "s";
    }
}
