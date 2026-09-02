package vn.svframe.svframemmo.cobblemon.fusion.render;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.cobblemon.fusion.FusionVisualBridge;

/** Common-side payload registration and server broadcasting for direct client renderer replacement. */
public final class FusionMorphNetworking {
    private static boolean registered;

    private FusionMorphNetworking() { }

    public static synchronized void register() {
        if (registered) return;
        PayloadTypeRegistry.playS2C().register(FusionMorphPayload.ID, FusionMorphPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> FusionVisualBridge.syncViewer(handler.player)));
        registered = true;
    }

    public static void broadcast(MinecraftServer server, FusionMorphPayload payload) {
        if (server == null || payload == null) return;
        if (payload.active()) {
            ServerPlayerEntity subject = server.getPlayerManager().getPlayer(payload.playerUuid());
            if (subject != null && !ServerPlayNetworking.canSend(subject, FusionMorphPayload.ID)) {
                throw new IllegalStateException(
                        "Fusion renderer is unavailable on the fused player's client. " +
                        "Install the same SVFrameMMO: Cobblemon Integration JAR in the client modpack."
                );
            }
        }
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) send(viewer, payload);
    }

    public static void send(ServerPlayerEntity viewer, FusionMorphPayload payload) {
        if (viewer != null && payload != null && ServerPlayNetworking.canSend(viewer, FusionMorphPayload.ID)) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }
}
