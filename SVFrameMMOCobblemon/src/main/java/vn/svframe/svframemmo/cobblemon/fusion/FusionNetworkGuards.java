package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.net.messages.server.pasture.PasturePokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.SwapPCPartyPokemonPacket;
import com.cobblemon.mod.common.net.messages.server.storage.pc.MovePartyPokemonToPCPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.util.function.Predicate;

/**
 * Guards Cobblemon storage mutations through Fabric's registered C2S receivers.
 *
 * This deliberately does not mix into Cobblemon packet-handler classes. Large server packs can load those
 * handlers from a preLaunch mod before Mixin has prepared every downstream config, which makes handler-target
 * mixins vulnerable to MixinTargetAlreadyLoadedException. Wrapping the already-registered Fabric receivers at
 * SERVER_STARTING preserves Cobblemon's original handler and blocks only packets that touch a fusion-locked Pokemon.
 */
public final class FusionNetworkGuards {
    private static final CustomPayload.Id<MovePartyPokemonToPCPacket> MOVE_PARTY_TO_PC =
            new CustomPayload.Id<>(Identifier.of("cobblemon", "move_party_pokemon_to_pc"));
    private static final CustomPayload.Id<SwapPCPartyPokemonPacket> SWAP_PC_PARTY =
            new CustomPayload.Id<>(Identifier.of("cobblemon", "swap_pc_party_pokemon"));
    private static final CustomPayload.Id<PasturePokemonPacket> PASTURE_POKEMON =
            new CustomPayload.Id<>(Identifier.of("cobblemon", "pasture_pokemon"));

    private static boolean registered;

    private FusionNetworkGuards() { }

    public static synchronized void register(FusionService fusions) {
        if (registered) return;

        guard(MOVE_PARTY_TO_PC,
                packet -> fusions.isPokemonLocked(packet.getPokemonID()),
                "move party Pokemon to PC");
        guard(SWAP_PC_PARTY,
                packet -> fusions.isPokemonLocked(packet.getPartyPokemonID())
                        || fusions.isPokemonLocked(packet.getPcPokemonID()),
                "swap party and PC Pokemon");
        guard(PASTURE_POKEMON,
                packet -> fusions.isPokemonLocked(packet.getPokemonId()),
                "move Pokemon to pasture");

        registered = true;
        SVFrameMMOCobblemon.LOG.info("Fusion storage network guards installed");
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPayload> void guard(CustomPayload.Id<T> type, Predicate<T> blocked, String operation) {
        ServerPlayNetworking.PlayPayloadHandler<T> original =
                (ServerPlayNetworking.PlayPayloadHandler<T>) ServerPlayNetworking.unregisterGlobalReceiver(type.id());
        if (original == null) {
            throw new IllegalStateException("Cobblemon C2S receiver is not registered for " + type.id());
        }

        boolean installed = ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            if (blocked.test(payload)) {
                context.player().sendMessage(Text.literal("That Pokemon is locked while fusion is active; cannot " + operation + "."), true);
                return;
            }
            original.receive(payload, context);
        });

        if (!installed) {
            ServerPlayNetworking.registerGlobalReceiver(type, original);
            throw new IllegalStateException("Could not install fusion guard for Cobblemon receiver " + type.id());
        }
    }
}
