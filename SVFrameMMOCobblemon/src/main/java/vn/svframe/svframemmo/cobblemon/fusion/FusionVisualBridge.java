package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.nucleoid.disguiselib.api.EntityDisguise;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side packet disguise bridge for fusion.
 *
 * The real gameplay entity remains the ServerPlayerEntity. The PokemonEntity stored here is only a non-world
 * disguise template consumed by DisguiseLib's packet rewriting. It is never spawned into the ServerWorld.
 */
public final class FusionVisualBridge {
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public static void verifyBackend() {
        if (!EntityDisguise.class.isAssignableFrom(Entity.class))
            throw new IllegalStateException("DisguiseLib mixin is not applied to Entity; server-side fusion disguise is unavailable");
    }

    public void start(ServerPlayerEntity player, Pokemon pokemon) {
        UUID playerId = player.getUuid();
        if (states.containsKey(playerId))
            throw new IllegalStateException("Fusion disguise already active for " + playerId);

        PokemonEntity deployed = pokemon.getEntity();
        if (deployed != null && !deployed.isRemoved())
            throw new IllegalStateException("Fusion disguise requires the party Pokemon to be recalled; refusing duplicate Pokemon entity");

        EntityDisguise disguise = disguise(player);
        if (disguise.isDisguised())
            throw new IllegalStateException("Player already has a non-fusion disguise; refusing to overwrite it");

        PokemonEntity visual = createVisual(player, pokemon);
        State state = new State(playerId, visual);
        if (states.putIfAbsent(playerId, state) != null)
            throw new IllegalStateException("Fusion disguise already active for " + playerId);

        try {
            disguise.disguiseAs(visual);
            if (!disguise.isDisguised() || disguise.getDisguiseEntity() != visual) {
                states.remove(playerId, state);
                throw new IllegalStateException("DisguiseLib did not retain the Cobblemon PokemonEntity disguise template");
            }
        } catch (RuntimeException error) {
            states.remove(playerId, state);
            if (disguise.isDisguised() && disguise.getDisguiseEntity() == visual) {
                try { disguise.removeDisguise(); }
                catch (RuntimeException ignored) { }
            }
            throw error;
        }
    }

    /** Missing players or externally replaced disguises are returned so FusionService can terminate stale sessions. */
    public Set<UUID> tick(MinecraftServer server) {
        if (states.isEmpty()) return Set.of();
        Set<UUID> invalid = new HashSet<>();
        for (State state : states.values()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(state.playerUuid());
            if (player == null) {
                invalid.add(state.playerUuid());
                continue;
            }
            EntityDisguise disguise = disguise(player);
            if (!disguise.isDisguised() || disguise.getDisguiseEntity() != state.visual())
                invalid.add(state.playerUuid());
        }
        return invalid;
    }

    public void stop(ServerPlayerEntity player, UUID playerId) {
        State removed = states.remove(playerId);
        if (removed == null || player == null) return;
        EntityDisguise disguise = disguise(player);
        if (disguise.isDisguised() && disguise.getDisguiseEntity() == removed.visual())
            disguise.removeDisguise();
    }

    private static PokemonEntity createVisual(ServerPlayerEntity player, Pokemon pokemon) {
        PokemonProperties properties = new PokemonProperties();
        properties.setSpecies(pokemon.getSpecies().getResourceIdentifier().toString());
        properties.setLevel(pokemon.getLevel());
        properties.setShiny(pokemon.getShiny());
        properties.setGender(pokemon.getGender());
        properties.setScaleModifier(pokemon.getScaleModifier());
        properties.setAspects(Set.copyOf(pokemon.getAspects()));

        if (pokemon.getForm() != null) {
            String form = pokemon.getForm().getName();
            if (form != null && !form.isBlank()) properties.setForm(form);
        }

        PokemonEntity visual = properties.createEntity(player.getServerWorld());
        if (visual == null)
            throw new IllegalStateException("Cobblemon could not create fusion disguise entity for "
                    + pokemon.getSpecies().getResourceIdentifier());

        visual.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        visual.setVelocity(player.getVelocity());
        visual.setOnGround(player.isOnGround());
        visual.setBodyYaw(player.bodyYaw);
        visual.setHeadYaw(player.headYaw);
        visual.hideNameRendering();
        return visual;
    }

    private static EntityDisguise disguise(ServerPlayerEntity player) {
        if (!((Object) player instanceof EntityDisguise disguise))
            throw new IllegalStateException("ServerPlayerEntity is missing DisguiseLib's EntityDisguise mixin");
        return disguise;
    }

    private record State(UUID playerUuid, PokemonEntity visual) { }
}
