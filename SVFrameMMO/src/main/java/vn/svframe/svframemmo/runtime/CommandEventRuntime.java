package vn.svframe.svframemmo.runtime;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.MMOCommandEvent;

import java.util.Locale;
import java.util.Set;

/** Gates the native RPG command roots through the public cancellable command event. */
public final class CommandEventRuntime {
    private static final Set<String> ROOTS = Set.of(
            "svframemmo", "mmo",
            "class", "c", "attributes", "att", "stats", "skills", "s", "player", "p", "profile",
            "skilltrees", "st", "trees", "tree",
            "teachskill", "giveskill", "pvpmode", "deposit");

    private CommandEventRuntime() { }

    public static boolean cancel(ServerCommandSource source, String rawCommand) {
        if (source == null || rawCommand == null) return false;
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return false;
        String command = rawCommand.stripLeading();
        while (command.startsWith("/")) command = command.substring(1).stripLeading();
        if (command.isEmpty()) return false;
        int split = command.indexOf(' ');
        String root = (split < 0 ? command : command.substring(0, split)).toLowerCase(Locale.ROOT);
        if (!ROOTS.contains(root)) return false;
        return new MMOCommandEvent(SVFrameMMO.playerData().get(player), root).call().isCancelled();
    }
}
