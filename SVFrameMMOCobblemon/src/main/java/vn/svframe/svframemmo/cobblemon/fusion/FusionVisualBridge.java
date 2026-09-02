package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionMorphNetworking;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionMorphPayload;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative fusion render state. No Pokemon entity is spawned for Fusion and the player is never made
 * invisible. Clients render the real player entity through Cobblemon's Pokemon renderer using the synchronized
 * PokemonProperties snapshot.
 */
public final class FusionVisualBridge {
    private static volatile FusionVisualBridge activeBridge;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FusionVisualBridge() {
        FusionVisualBridge previous = activeBridge;
        if (previous != null && previous != this)
            throw new IllegalStateException("Only one FusionVisualBridge may own fusion render state");
        activeBridge = this;
    }

    public static void syncViewer(ServerPlayerEntity viewer) {
        FusionVisualBridge bridge = activeBridge;
        if (bridge != null) bridge.syncTo(viewer);
    }

    public void start(ServerPlayerEntity player, Pokemon pokemon) {
        UUID playerId = player.getUuid();
        if (states.containsKey(playerId))
            throw new IllegalStateException("Fusion render state already active for " + playerId);

        PokemonEntity deployed = pokemon.getEntity();
        if (deployed != null && !deployed.isRemoved())
            throw new IllegalStateException("Fusion rendering requires the party Pokemon to be recalled");

        String properties = encodeVisual(pokemon);
        State state = new State(playerId, pokemon.getUuid(), properties);
        if (states.putIfAbsent(playerId, state) != null)
            throw new IllegalStateException("Fusion render state already active for " + playerId);

        try {
            FusionMorphNetworking.broadcast(player.getServerWorld().getServer(),
                    new FusionMorphPayload(playerId, true, properties));
        } catch (RuntimeException error) {
            states.remove(playerId, state);
            throw error;
        }
    }

    /** Missing players/Pokemon are returned so FusionService can terminate stale sessions. */
    public Set<UUID> tick(MinecraftServer server) {
        if (states.isEmpty()) return Set.of();
        Set<UUID> invalid = new HashSet<>();

        for (State state : List.copyOf(states.values())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(state.playerUuid);
            if (player == null) {
                invalid.add(state.playerUuid);
                continue;
            }
            Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(state.pokemonUuid);
            if (pokemon == null) {
                invalid.add(state.playerUuid);
                continue;
            }

            // Re-publish if form/aspects/shiny/scale changes while the session remains active.
            if (player.age % 20 == 0) {
                String current = encodeVisual(pokemon);
                if (!current.equals(state.properties)) {
                    state.properties = current;
                    FusionMorphNetworking.broadcast(server,
                            new FusionMorphPayload(state.playerUuid, true, current));
                }
            }
        }
        return invalid;
    }

    public void stop(ServerPlayerEntity player, UUID playerId) {
        State removed = states.remove(playerId);
        if (removed == null || player == null) return;
        FusionMorphNetworking.broadcast(player.getServerWorld().getServer(), FusionMorphPayload.clear(playerId));
    }

    private void syncTo(ServerPlayerEntity viewer) {
        for (State state : List.copyOf(states.values())) {
            FusionMorphNetworking.send(viewer,
                    new FusionMorphPayload(state.playerUuid, true, state.properties));
        }
    }

    /**
     * TRANSFORM is Cobblemon's own canonical visual extractor. In particular it uses formOnlyShowdownId() and the
     * actual aspect set, avoiding the incorrect FormData#getName conversion used by the packet-disguise prototype.
     */
    private static String encodeVisual(Pokemon pokemon) {
        PokemonProperties properties = pokemon.createPokemonProperties(PokemonPropertyExtractor.TRANSFORM);
        properties.setShiny(pokemon.getShiny());
        properties.setLevel(pokemon.getLevel());
        properties.setScaleModifier(pokemon.getScaleModifier());
        return properties.asString(" ");
    }

    private static final class State {
        private final UUID playerUuid;
        private final UUID pokemonUuid;
        private volatile String properties;

        private State(UUID playerUuid, UUID pokemonUuid, String properties) {
            this.playerUuid = playerUuid;
            this.pokemonUuid = pokemonUuid;
            this.properties = properties;
        }
    }
}
