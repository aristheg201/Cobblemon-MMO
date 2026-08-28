package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionMorphNetworking;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionMorphPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Integration-owned fusion render state. The server never spawns a visual proxy: clients replace the player renderer
 * with a non-world Cobblemon PokemonEntity created from these properties.
 */
public final class FusionVisualBridge {
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void start(ServerPlayerEntity player, Pokemon pokemon) {
        UUID playerId = player.getUuid();
        if (states.containsKey(playerId)) throw new IllegalStateException("Fusion visual already active for " + playerId);
        PokemonEntity deployed = pokemon.getEntity();
        if (deployed != null && !deployed.isRemoved())
            throw new IllegalStateException("Fusion render requires the party Pokemon to be recalled; refusing duplicate Pokemon entity");

        String properties = buildProperties(pokemon);
        State state = new State(playerId, properties);
        if (states.putIfAbsent(playerId, state) != null)
            throw new IllegalStateException("Fusion visual already active for " + playerId);
        FusionMorphNetworking.broadcast(player.getServerWorld().getServer(), new FusionMorphPayload(playerId, true, properties));
    }

    /** Missing players are returned so FusionService can terminate stale sessions. */
    public Set<UUID> tick(MinecraftServer server) {
        if (states.isEmpty()) return Set.of();
        java.util.HashSet<UUID> invalid = new java.util.HashSet<>();
        for (State state : states.values())
            if (server.getPlayerManager().getPlayer(state.playerUuid()) == null) invalid.add(state.playerUuid());
        return invalid;
    }

    public void syncTo(ServerPlayerEntity viewer) {
        for (State state : states.values())
            FusionMorphNetworking.send(viewer, new FusionMorphPayload(state.playerUuid(), true, state.properties()));
    }

    public void stop(ServerPlayerEntity player, UUID playerId) {
        State removed = states.remove(playerId);
        if (removed == null || player == null) return;
        FusionMorphNetworking.broadcast(player.getServerWorld().getServer(), FusionMorphPayload.clear(playerId));
    }

    private static String buildProperties(Pokemon pokemon) {
        String species = pokemon.getSpecies().getResourceIdentifier().toString();
        List<String> parts = new ArrayList<>();
        parts.add(species);
        parts.add("level=" + pokemon.getLevel());
        if (pokemon.getShiny()) parts.add("shiny=true");
        if (pokemon.getForm() != null) {
            String form = pokemon.getForm().getName();
            if (form != null && !form.isBlank() && !"normal".equalsIgnoreCase(form) && !"standard".equalsIgnoreCase(form))
                parts.add("form=" + form.replace(' ', '_'));
        }
        if (pokemon.getGender() != null)
            parts.add("gender=" + pokemon.getGender().name().toLowerCase(Locale.ROOT));
        return String.join(" ", parts);
    }

    private record State(UUID playerUuid, String properties) { }
}
