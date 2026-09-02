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

    /**
     * A real fusion morph replaces the local player renderer. That cannot be represented to the local player through
     * vanilla entity packets: the client must have the integration's client receiver/mixin loaded. Refuse to commit an
     * active morph when the fused player's own client does not advertise the payload instead of silently leaving the
     * player model visible and pretending fusion rendering succeeded.
     */
    public static void broadcast(MinecraftServer server, FusionMorphPayload payload) {
        if (server == null || payload == null) return;

        if (payload.active()) {
            ServerPlayerEntity subject = server.getPlayerManager().getPlayer(payload.playerUuid());
            if (subject != null && !ServerPlayNetworking.canSend(subject, FusionMorphPayload.ID)) {
                throw new IllegalStateException(
                        "Fusion morph renderer is unavailable on the fused player's client. " +
                        "Install the same SVFrameMMO: Cobblemon Integration JAR in the client modpack."
                );
            }
        }

        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) send(viewer, payload);
    }

    public static void send(ServerPlayerEntity viewer, FusionMorphPayload payload) {
        if (viewer == null || payload == null) return;
        if (ServerPlayNetworking.canSend(viewer, FusionMorphPayload.ID)) ServerPlayNetworking.send(viewer, payload);
    }
}
