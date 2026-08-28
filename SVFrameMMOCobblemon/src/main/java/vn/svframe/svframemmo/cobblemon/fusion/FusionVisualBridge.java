package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.pixelpitstop.pocketmorph.CobblemonPropertiesResolver;
import com.pixelpitstop.pocketmorph.CobblemonSpeciesResolver;
import com.pixelpitstop.pocketmorph.ServerDisguiseSyncManager;
import com.pixelpitstop.pocketmorph.SyncedDisguiseData;
import com.pixelpitstop.pocketmorph.networking.ModPayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real fusion morph presentation backed by PocketMorph's authoritative disguise state.
 * The player is the only visible fused entity; the party Pokemon must already be recalled.
 */
public final class FusionVisualBridge {
    private static final String SOURCE = "SVFrameMMO Cobblemon fusion";
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void start(ServerPlayerEntity player, Pokemon pokemon) {
        UUID playerId = player.getUuid();
        if (states.containsKey(playerId)) throw new IllegalStateException("Fusion visual already active for " + playerId);

        PokemonEntity deployed = pokemon.getEntity();
        if (deployed != null && !deployed.isRemoved())
            throw new IllegalStateException("Fusion render requires the party Pokemon to be recalled; refusing duplicate Pokemon entity");

        SyncedDisguiseData previous = ServerDisguiseSyncManager.get(playerId);
        String species = CobblemonSpeciesResolver.normalizeSpeciesIdentifier(
                pokemon.getSpecies().getResourceIdentifier().toString())
                .orElseThrow(() -> new IllegalStateException("PocketMorph could not resolve " + pokemon.getSpecies().getName()));
        String properties = buildProperties(species, pokemon);
        SyncedDisguiseData requested = new SyncedDisguiseData(
                playerId,
                species,
                properties,
                false,
                false,
                false,
                false,
                false,
                null,
                null,
                pokemon.getDisplayName(false).getString(),
                true,
                true,
                false,
                1.0f,
                1.0f,
                false,
                0.0f,
                false,
                0.0f,
                0.0f,
                0.0f
        );

        try {
            ServerDisguiseSyncManager.upsertAdministrative(player, requested, SOURCE);
            SyncedDisguiseData applied = ServerDisguiseSyncManager.get(playerId);
            if (applied == null || !CobblemonSpeciesResolver.speciesMatchesExactly(species, applied.species()))
                throw new IllegalStateException("PocketMorph rejected the requested fusion disguise for " + species);

            states.put(playerId, new State(playerId, species, previous));
            // Authoritative sync uses forceLocal for the owner, so PocketMorph replaces the local player's renderer too.
            ModPayloads.broadcastAuthoritativeSyncedState(player.getServerWorld().getServer(), applied, SOURCE);
        } catch (RuntimeException error) {
            restore(player, playerId, previous);
            throw error;
        }
    }

    /** Returns fused players whose PocketMorph disguise was externally removed or replaced. */
    public Set<UUID> tick(MinecraftServer server) {
        if (states.isEmpty()) return Set.of();
        java.util.HashSet<UUID> invalid = new java.util.HashSet<>();
        for (State state : states.values()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(state.playerUuid());
            SyncedDisguiseData active = ServerDisguiseSyncManager.get(state.playerUuid());
            if (player == null || active == null || !CobblemonSpeciesResolver.speciesMatchesExactly(state.species(), active.species()))
                invalid.add(state.playerUuid());
        }
        return invalid;
    }

    public void stop(ServerPlayerEntity player, UUID playerId) {
        State state = states.remove(playerId);
        if (state == null) return;
        restore(player, playerId, state.previous());
    }

    private static void restore(ServerPlayerEntity player, UUID playerId, SyncedDisguiseData previous) {
        if (player != null && previous != null) {
            ServerDisguiseSyncManager.upsertAdministrative(player, previous, SOURCE + " restore");
            SyncedDisguiseData restored = ServerDisguiseSyncManager.get(playerId);
            if (restored != null)
                ModPayloads.broadcastAuthoritativeSyncedState(player.getServerWorld().getServer(), restored, SOURCE + " restore");
            return;
        }

        ServerDisguiseSyncManager.remove(playerId);
        if (player != null)
            ModPayloads.broadcastAuthoritativeClear(player.getServerWorld().getServer(), playerId, SOURCE);
    }

    private static String buildProperties(String species, Pokemon pokemon) {
        List<String> parts = new ArrayList<>();
        parts.add("level=" + pokemon.getLevel());
        if (pokemon.getShiny()) parts.add("shiny");

        if (pokemon.getForm() != null) {
            String form = pokemon.getForm().getName();
            if (form != null && !form.isBlank() && !"normal".equalsIgnoreCase(form) && !"standard".equalsIgnoreCase(form))
                parts.add("form=" + form.replace(' ', '_'));
        }

        if (pokemon.getGender() != null)
            parts.add("gender=" + pokemon.getGender().name().toLowerCase(Locale.ROOT));

        String requested = String.join(" ", parts);
        CobblemonPropertiesResolver.ValidationResult validation = CobblemonPropertiesResolver.validate(species, requested);
        if (!validation.valid())
            throw new IllegalStateException("PocketMorph rejected Pokemon properties: " + validation.detail());
        return validation.normalizedProperties();
    }

    private record State(UUID playerUuid, String species, SyncedDisguiseData previous) { }
}
