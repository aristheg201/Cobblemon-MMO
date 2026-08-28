package vn.svframe.svframemmo.cobblemon.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import vn.svframe.svframemmo.cobblemon.fusion.render.ClientFusionMorphState;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionMorphPayload;

/** Client half of the native Cobblemon fusion renderer. */
public final class SVFrameMMOCobblemonClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(FusionMorphPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientFusionMorphState.apply(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientFusionMorphState.clear());
    }
}
