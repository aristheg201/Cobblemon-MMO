package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native server-side morph presentation. The real party Pokemon is used as the visible avatar while
 * the player is hidden. Only currently fused players are synchronized.
 */
public final class FusionVisualBridge {
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public void start(ServerPlayerEntity player, Pokemon pokemon, PokemonEntity entity, boolean autoDeployed) {
        State state = new State(player.getUuid(), pokemon, entity, autoDeployed,
                player.isInvisible(), entity.isInvulnerable(), entity.hasNoGravity());
        State old = states.putIfAbsent(player.getUuid(), state);
        if (old != null) throw new IllegalStateException("Fusion visual already active for " + player.getUuid());
        player.setInvisible(true);
        entity.setInvulnerable(true);
        entity.setNoGravity(true);
        synchronize(player, entity);
    }

    /** Returns fused players whose visible Pokemon can no longer be synchronized safely. */
    public Set<UUID> tick(MinecraftServer server) {
        if (states.isEmpty()) return Set.of();
        java.util.HashSet<UUID> invalid = new java.util.HashSet<>();
        for (State state : states.values()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(state.playerUuid());
            PokemonEntity entity = state.entity();
            if (player == null || entity.isRemoved() || entity.getWorld() != player.getWorld()) {
                invalid.add(state.playerUuid());
                continue;
            }
            synchronize(player, entity);
        }
        return invalid;
    }

    public void stop(ServerPlayerEntity player, UUID playerId) {
        State state = states.remove(playerId);
        if (state == null) return;
        if (player != null) player.setInvisible(state.playerWasInvisible());
        PokemonEntity entity = state.entity();
        if (!entity.isRemoved()) {
            entity.setInvulnerable(state.entityWasInvulnerable());
            entity.setNoGravity(state.entityHadNoGravity());
            entity.setPokemonWalking(false);
            entity.setPokemonFlying(false);
        }
        if (state.autoDeployed()) state.pokemon().recall();
    }

    private static void synchronize(ServerPlayerEntity player, PokemonEntity entity) {
        Vec3d velocity = player.getVelocity();
        entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        entity.setVelocity(velocity);
        entity.setPokemonWalking(player.isOnGround() && velocity.horizontalLengthSquared() > 0.0025d);
        entity.setPokemonFlying(!player.isOnGround());
    }

    private record State(UUID playerUuid, Pokemon pokemon, PokemonEntity entity, boolean autoDeployed,
                         boolean playerWasInvisible, boolean entityWasInvulnerable, boolean entityHadNoGravity) { }
}
