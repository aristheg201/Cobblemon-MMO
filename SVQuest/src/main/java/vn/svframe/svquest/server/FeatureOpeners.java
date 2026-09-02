package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Native integration capabilities. Which feature uses which capability is selected by features.json. */
public final class FeatureOpeners {
    private FeatureOpeners() {}

    /**
     * Handles non-command feature actions. Interaction-only entries deliberately do not open the
     * foreign mod GUI: they only tell the player what real world block must be used.
     */
    public static boolean handle(ServerPlayerEntity player, String opener) {
        if (opener == null || opener.isBlank()) return false;
        return switch (opener) {
            case "interaction_battle_tower" -> {
                player.sendMessage(Text.literal("§eBattle Tower không có command. Hãy tới Holo Battle Tower terminal và right-click block để mở."), false);
                yield true;
            }
            case "interaction_expeditions" -> {
                player.sendMessage(Text.literal("§eExpeditions không có command. Hãy tới Expedition Board và right-click block để mở."), false);
                yield true;
            }
            case "soulbreeding" -> { openSoulBreeding(player); yield true; }
            default -> false;
        };
    }

    /** Only genuine server-opened features are allowed to satisfy feature.* from the quest button. */
    public static boolean signalsProgress(String opener) {
        return opener != null && !opener.isBlank() && !opener.startsWith("interaction_");
    }

    private static void openSoulBreeding(ServerPlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("soulbreeding")) {
            player.sendMessage(Text.literal("§cSoulBreeding chưa được nạp trên server."), false);
            return;
        }
        try {
            Class<?> permissions = Class.forName("org.dev.fil.soulbreeding.util.Permissions");
            Object permissionInstance = permissions.getField("INSTANCE").get(null);
            Method hasPermission = permissions.getMethod("hasPermission", ServerPlayerEntity.class, String.class);
            boolean allowed = Boolean.TRUE.equals(hasPermission.invoke(permissionInstance, player, "soulbreeding.breed"));
            if (!allowed) {
                player.sendMessage(Text.literal("§cBạn không có quyền dùng Sinh sản."), false);
                return;
            }
            Class<?> guiClass = Class.forName("org.dev.fil.soulbreeding.gui.NestListGui");
            Constructor<?> constructor = guiClass.getConstructor(ServerPlayerEntity.class);
            Object gui = constructor.newInstance(player);
            guiClass.getMethod("open").invoke(gui);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("SoulBreeding opener failed safely for {}: {}", player.getName().getString(), t.toString());
            player.sendMessage(Text.literal("§cKhông mở được Sinh sản lúc này."), false);
        }
    }
}
