package vn.svframe.svframemmo.cobblemon.fusion.render;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Common-side payload registration and server broadcasting for native fusion rendering. */
public final class FusionMorphNetworking {
    private static boolean registered;

    private FusionMorphNetworking() { }

    public static synchronized void register() {
        if (registered) return;
        PayloadTypeRegistry.playS2C().register(FusionMorphPayload.ID, FusionMorphPayload.CODEC);
        registered = true;
    }

    public static void broadcast(MinecraftServer server, FusionMorphPayload payload) {
        if (server == null || payload == null) return;
        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) send(viewer, payload);
    }

    public static void send(ServerPlayerEntity viewer, FusionMorphPayload payload) {
        if (viewer == null || payload == null) return;
        if (ServerPlayNetworking.canSend(viewer, FusionMorphPayload.ID)) ServerPlayNetworking.send(viewer, payload);
    }
}
