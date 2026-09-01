package vn.svframe.svquest.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import vn.svframe.svquest.SVQuest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Native integration capabilities. Which feature uses which capability is selected by features.json. */
public final class FeatureOpeners {
    private FeatureOpeners() {}

    public static boolean handle(ServerPlayerEntity player, String opener) {
        if (opener == null || opener.isBlank()) return false;
        return switch (opener) {
            case "battle_tower_nearby" -> { openBattleTower(player); yield true; }
            case "soulbreeding" -> { openSoulBreeding(player); yield true; }
            default -> false;
        };
    }

    private static void openBattleTower(ServerPlayerEntity player) {
        if (!FabricLoader.getInstance().isModLoaded("cobblemon_battle_tower")) {
            player.sendMessage(Text.literal("§cBattle Tower chưa được nạp trên server."), false);
            return;
        }
        try {
            ServerWorld world = player.getServerWorld();
            BlockPos origin = player.getBlockPos();
            Class<?> towerBlockClass = Class.forName("battle.tower.block.HoloBattleTowerBlock");
            BlockPos nearest = null;
            int nearestSq = Integer.MAX_VALUE;
            for (int dy = -8; dy <= 8; dy++) for (int dx = -16; dx <= 16; dx++) for (int dz = -16; dz <= 16; dz++) {
                BlockPos pos = origin.add(dx, dy, dz);
                if (!towerBlockClass.isInstance(world.getBlockState(pos).getBlock())) continue;
                int d = dx * dx + dy * dy + dz * dz;
                if (d < nearestSq) { nearestSq = d; nearest = new BlockPos(pos.getX(), pos.getY(), pos.getZ()); }
            }
            if (nearest == null) {
                player.sendMessage(Text.literal("§eHãy đứng gần Holo Battle Tower terminal rồi thử lại."), false);
                return;
            }
            Class<?> helper = Class.forName("battle.tower.platform.NetworkHelper");
            helper.getMethod("sendBattleTowerOpenScreen", ServerPlayerEntity.class, BlockPos.class).invoke(null, player, nearest);
        } catch (Throwable t) {
            SVQuest.LOGGER.warn("Battle Tower opener failed safely for {}: {}", player.getName().getString(), t.toString());
            player.sendMessage(Text.literal("§cKhông mở được Battle Tower terminal lúc này."), false);
        }
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
