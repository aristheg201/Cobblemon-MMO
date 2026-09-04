package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionRawPacketSender;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-only Fusion Stand runtime.
 *
 * The authoritative ServerPlayerEntity is never replaced, hidden or modified. Fusion manifests the selected party
 * Pokemon as a second packet-only PokemonEntity hovering behind the player. The Stand is not inserted into the
 * authoritative world. A vanilla scoreboard team with collision rule NEVER is synchronized by the server so even
 * unmodified clients exclude the packet-only Pokemon from their entity-push predicate.
 */
public final class FusionVisualBridge {
    private static final int STAND_ENTITY_BASE = 1_000_000_000;
    private static final int VISUAL_REFRESH_INTERVAL = 20;
    private static final String STAND_COLLISION_TEAM = "svfusionstand";
    private static final double BACK_OFFSET = 1.20D;
    private static final double SIDE_OFFSET = 0.65D;
    private static final double HEIGHT_OFFSET = 0.55D;
    private static final double HOVER_AMPLITUDE = 0.08D;

    private static final List<String> PHYSICAL_ANIMATIONS = List.of("physical", "attack", "special");
    private static final List<String> SPECIAL_ANIMATIONS = List.of("special", "physical");
    private static final List<String> STATUS_ANIMATIONS = List.of("status", "special", "physical");

    private static volatile FusionVisualBridge activeBridge;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FusionVisualBridge() {
        FusionVisualBridge previous = activeBridge;
        if (previous != null && previous != this)
            throw new IllegalStateException("Only one FusionVisualBridge may own Fusion Stand packet state");
        activeBridge = this;
    }

    /**
     * Observes vanilla player tracking packets so newly tracking viewers receive/destroy the additional Stand entity.
     * Player packets themselves are never cancelled or rewritten.
     */
    public static boolean rewriteOutgoing(ServerPlayerEntity viewer, Packet<?> packet) {
        FusionVisualBridge bridge = activeBridge;
        return bridge != null && bridge.observeOutgoing(viewer, packet);
    }

    /** Successful direct melee/basic hit. Missed clicks never arrive here. */
    public static void playSuccessfulBasicAttack(ServerPlayerEntity player) {
        FusionVisualBridge bridge = activeBridge;
        if (bridge != null) bridge.playBasicAttack(player);
    }

    /** Successful Cobblemon /pokeskill execution. Move category selects the Stand animation family. */
    public static void playMoveAnimation(ServerPlayerEntity player, MoveTemplate move) {
        FusionVisualBridge bridge = activeBridge;
        if (bridge != null) bridge.playMove(player, move);
    }

    public void start(ServerPlayerEntity player, Pokemon pokemon) {
        UUID playerId = player.getUuid();
        if (states.containsKey(playerId))
            throw new IllegalStateException("Fusion Stand already active for " + playerId);

        PokemonEntity deployed = pokemon.getEntity();
        if (deployed != null && !deployed.isRemoved())
            throw new IllegalStateException("Fusion Stand requires the party Pokemon to be recalled; refusing duplicate Pokemon entity");

        String signature = visualSignature(pokemon);
        PokemonEntity visual = createVisual(player, pokemon);
        Frame frame = syncStandState(player, visual);
        State state = new State(playerId, pokemon.getUuid(), visual, player.getId(),
                player.getServerWorld().getRegistryKey(), signature, frame);
        if (states.putIfAbsent(playerId, state) != null)
            throw new IllegalStateException("Fusion Stand already active for " + playerId);

        try {
            registerCollisionlessStand(player.getServer(), visual);
            syncViewers(player, state, true);
        } catch (RuntimeException error) {
            states.remove(playerId, state);
            destroyForKnownViewers(player.getServer(), state);
            unregisterCollisionlessStand(player.getServer(), visual);
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

            boolean worldChanged = state.playerEntityId != player.getId()
                    || !state.worldKey.equals(player.getServerWorld().getRegistryKey());
            boolean appearanceChanged = !worldChanged
                    && player.age % VISUAL_REFRESH_INTERVAL == 0
                    && !state.visualSignature.equals(visualSignature(pokemon));

            if (worldChanged || appearanceChanged) {
                respawnVisual(player, pokemon, state);
                continue;
            }

            Frame previous = state.frame;
            Frame current = syncStandState(player, state.visual);
            state.frame = current;
            syncViewers(player, state, !current.equals(previous));
        }
        return invalid;
    }

    public void stop(ServerPlayerEntity player, UUID playerId) {
        State removed = states.remove(playerId);
        if (removed == null) return;
        MinecraftServer server = player == null ? removed.visual.getServer() : player.getServer();
        destroyForKnownViewers(server, removed);
        unregisterCollisionlessStand(server, removed.visual);
    }

    private boolean observeOutgoing(ServerPlayerEntity viewer, Packet<?> packet) {
        if (viewer == null || packet == null || states.isEmpty()) return false;

        if (packet instanceof EntitySpawnS2CPacket spawn) {
            Entity original = viewer.getServerWorld().getEntityById(spawn.getEntityId());
            if (original instanceof ServerPlayerEntity subject) {
                State state = states.get(subject.getUuid());
                if (state != null && !subject.getUuid().equals(viewer.getUuid())) {
                    syncStandState(subject, state.visual);
                    spawnForViewer(viewer, state);
                }
            }
        } else if (packet instanceof EntitiesDestroyS2CPacket destroy) {
            int[] ids = destroy.getEntityIds().toIntArray();
            for (State state : states.values()) {
                for (int id : ids) {
                    if (id != state.playerEntityId) continue;
                    if (state.viewers.remove(viewer.getUuid()) && viewer.networkHandler.isConnectionOpen())
                        raw(viewer, new EntitiesDestroyS2CPacket(state.visual.getId()));
                    break;
                }
            }
        }

        // Legacy forceInvisible disguise path is intentionally removed: the real player remains fully visible.
        return false;
    }

    private void playBasicAttack(ServerPlayerEntity player) {
        if (player == null) return;
        State state = states.get(player.getUuid());
        if (state == null || player.age <= state.suppressBasicUntilAge) return;
        playAnimation(player, state, AnimationKind.PHYSICAL, false);
    }

    private void playMove(ServerPlayerEntity player, MoveTemplate move) {
        if (player == null || move == null) return;
        State state = states.get(player.getUuid());
        if (state == null) return;

        AnimationKind kind;
        if (move.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL()) kind = AnimationKind.PHYSICAL;
        else if (move.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS()) kind = AnimationKind.STATUS;
        else kind = AnimationKind.SPECIAL;

        state.suppressBasicUntilAge = player.age + 2;
        playAnimation(player, state, kind, kind == AnimationKind.PHYSICAL);
    }

    private void playAnimation(ServerPlayerEntity player, State state, AnimationKind kind, boolean physicalPulse) {
        if (!player.networkHandler.isConnectionOpen()) return;
        syncStandState(player, state.visual);
        syncViewers(player, state, false);

        LinkedHashSet<String> candidates = new LinkedHashSet<>(switch (kind) {
            case PHYSICAL -> PHYSICAL_ANIMATIONS;
            case SPECIAL -> SPECIAL_ANIMATIONS;
            case STATUS -> STATUS_ANIMATIONS;
        });
        CustomPayloadS2CPacket packet = new CustomPayloadS2CPacket(
                new PlayPosableAnimationPacket(state.visual.getId(), candidates, List.of()));
        sendToCurrentViewers(player, state, packet);

        // A small forward pulse makes a physical Stand cast read as an attack without turning every basic hit into a dash.
        if (physicalPulse) state.physicalPulseUntilAge = player.age + 5;
    }

    private void respawnVisual(ServerPlayerEntity player, Pokemon pokemon, State state) {
        destroyForKnownViewers(player.getServer(), state);
        state.visualSignature = visualSignature(pokemon);
        state.visual = createVisual(player, pokemon);
        state.playerEntityId = player.getId();
        state.worldKey = player.getServerWorld().getRegistryKey();
        state.frame = syncStandState(player, state.visual);
        state.lastTransform = null;
        state.viewers.clear();
        registerCollisionlessStand(player.getServer(), state.visual);
        syncViewers(player, state, true);
    }

    private void syncViewers(ServerPlayerEntity player, State state, boolean sendMetadata) {
        Collection<ServerPlayerEntity> tracking = PlayerLookup.tracking(player);
        UUID ownerId = player.getUuid();

        Iterator<UUID> iterator = state.viewers.iterator();
        while (iterator.hasNext()) {
            UUID viewerId = iterator.next();
            ServerPlayerEntity viewer = player.getServer().getPlayerManager().getPlayer(viewerId);
            boolean current = viewer != null
                    && viewer.networkHandler.isConnectionOpen()
                    && (viewerId.equals(ownerId) || tracking.contains(viewer));
            if (current) continue;
            iterator.remove();
            if (viewer != null && viewer.networkHandler.isConnectionOpen())
                raw(viewer, new EntitiesDestroyS2CPacket(state.visual.getId()));
        }

        Transform currentTransform = Transform.capture(state.visual);
        boolean transformChanged = !currentTransform.equals(state.lastTransform);

        syncViewer(player, state, sendMetadata, transformChanged);
        for (ServerPlayerEntity viewer : tracking) {
            syncViewer(viewer, state, sendMetadata, transformChanged);
        }
        state.lastTransform = currentTransform;
    }

    private static void syncViewer(ServerPlayerEntity viewer, State state, boolean sendMetadata, boolean transformChanged) {
        if (viewer == null || !viewer.networkHandler.isConnectionOpen()) return;
        UUID viewerId = viewer.getUuid();
        if (state.viewers.add(viewerId)) {
            spawnForViewer(viewer, state);
            return;
        }
        if (transformChanged) sendTransform(viewer, state.visual);
        if (sendMetadata) sendCoreVisualMetadata(viewer, state.visual.getId(), state.frame);
    }

    private static void sendToCurrentViewers(ServerPlayerEntity player, State state, Packet<?> packet) {
        for (UUID viewerId : state.viewers) {
            ServerPlayerEntity viewer = player.getServer().getPlayerManager().getPlayer(viewerId);
            if (viewer != null && viewer.networkHandler.isConnectionOpen()) raw(viewer, packet);
        }
    }

    private static void spawnForViewer(ServerPlayerEntity viewer, State state) {
        if (!viewer.networkHandler.isConnectionOpen()) return;
        raw(viewer, new EntitiesDestroyS2CPacket(state.visual.getId()));
        raw(viewer, pokemonSpawn(state.visual));
        sendCoreVisualMetadata(viewer, state.visual.getId(), state.frame);
        sendTransform(viewer, state.visual);
        state.viewers.add(viewer.getUuid());
    }

    private static void destroyForKnownViewers(MinecraftServer server, State state) {
        if (server == null) {
            state.viewers.clear();
            return;
        }
        for (UUID viewerId : state.viewers) {
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(viewerId);
            if (viewer != null && viewer.networkHandler.isConnectionOpen())
                raw(viewer, new EntitiesDestroyS2CPacket(state.visual.getId()));
        }
        state.viewers.clear();
    }

    /**
     * Vanilla client entity collision checks consult scoreboard collision rules for both participants. ServerScoreboard
     * broadcasts team creation/rule/member changes as TeamS2CPacket, so this removes client-side push without requiring
     * the Integration mod on the client. The holder key is the fake entity UUID, exactly what Entity uses for teams.
     */
    private static void registerCollisionlessStand(MinecraftServer server, PokemonEntity visual) {
        if (server == null || visual == null) return;
        var scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(STAND_COLLISION_TEAM);
        if (team == null) team = scoreboard.addTeam(STAND_COLLISION_TEAM);
        if (team.getCollisionRule() != AbstractTeam.CollisionRule.NEVER)
            team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        scoreboard.addScoreHolderToTeam(visual.getNameForScoreboard(), team);
    }

    private static void unregisterCollisionlessStand(MinecraftServer server, PokemonEntity visual) {
        if (server == null || visual == null) return;
        var scoreboard = server.getScoreboard();
        Team team = scoreboard.getTeam(STAND_COLLISION_TEAM);
        if (team != null && team.getPlayerList().contains(visual.getNameForScoreboard()))
            scoreboard.removeScoreHolderFromTeam(visual.getNameForScoreboard(), team);
    }

    /** Cobblemon's spawn payload supplies species/form/aspects/shiny/scale/pose around the nested vanilla transform. */
    private static CustomPayloadS2CPacket pokemonSpawn(PokemonEntity visual) {
        EntitySpawnS2CPacket vanilla = new EntitySpawnS2CPacket(
                visual.getId(),
                visual.getUuid(),
                visual.getX(), visual.getY(), visual.getZ(),
                visual.getPitch(), visual.getYaw(),
                visual.getType(),
                0,
                visual.getVelocity(),
                visual.getHeadYaw()
        );
        return new CustomPayloadS2CPacket(new SpawnPokemonPacket(visual, vanilla));
    }

    private static void sendTransform(ServerPlayerEntity viewer, PokemonEntity visual) {
        raw(viewer, new EntityPositionS2CPacket(visual));
        raw(viewer, new EntitySetHeadYawS2CPacket(visual, angle(visual.getHeadYaw())));
        raw(viewer, new EntityVelocityUpdateS2CPacket(visual.getId(), visual.getVelocity()));
    }

    private static void sendCoreVisualMetadata(ServerPlayerEntity viewer, int entityId, Frame frame) {
        List<DataTracker.SerializedEntry<?>> entries = List.of(
                DataTracker.SerializedEntry.of(PokemonEntity.getMOVING(), frame.moving),
                DataTracker.SerializedEntry.of(PokemonEntity.getPOSE_TYPE(), frame.pose),
                DataTracker.SerializedEntry.of(PokemonEntity.getHIDE_LABEL(), true)
        );
        raw(viewer, new EntityTrackerUpdateS2CPacket(entityId, entries));
    }

    private static byte angle(float degrees) {
        return (byte) ((int) (degrees * 256.0F / 360.0F));
    }

    private static int standEntityId(int playerEntityId) {
        if (playerEntityId < 0 || playerEntityId > Integer.MAX_VALUE - STAND_ENTITY_BASE)
            throw new IllegalStateException("Player entity id cannot be mapped to a Fusion Stand id: " + playerEntityId);
        return STAND_ENTITY_BASE + playerEntityId;
    }

    private static UUID standEntityUuid(UUID playerUuid) {
        return UUID.nameUUIDFromBytes(("svframemmo_cobblemon:fusion-stand:" + playerUuid)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Frame syncStandState(ServerPlayerEntity player, PokemonEntity visual) {
        Vec3d position = standPosition(player);
        visual.refreshPositionAndAngles(position.x, position.y, position.z, player.getYaw(), 0.0F);
        visual.setVelocity(Vec3d.ZERO);
        visual.setOnGround(false);
        visual.setBodyYaw(player.bodyYaw);
        visual.setHeadYaw(player.headYaw);
        visual.setSprinting(false);
        visual.setSneaking(false);
        visual.setSwimming(false);
        visual.setGlowing(player.isGlowing());
        visual.setInvisible(false);
        visual.setOnFire(false);

        PoseType pose = visual.canFly() ? PoseType.HOVER : PoseType.STAND;
        visual.getDataTracker().set(PokemonEntity.getMOVING(), false);
        visual.getDataTracker().set(PokemonEntity.getPOSE_TYPE(), pose);
        visual.getDataTracker().set(PokemonEntity.getHIDE_LABEL(), true);
        visual.setPokemonWalking(false);
        visual.setPokemonFlying(pose == PoseType.HOVER);
        return new Frame(pose, false);
    }

    private static Vec3d standPosition(ServerPlayerEntity player) {
        double yaw = Math.toRadians(player.getYaw());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double pulse = 0.0D;
        State state = activeBridge == null ? null : activeBridge.states.get(player.getUuid());
        if (state != null && player.age <= state.physicalPulseUntilAge) pulse = 0.55D;

        double back = Math.max(0.45D, BACK_OFFSET - pulse);
        double x = player.getX() + sin * back - cos * SIDE_OFFSET;
        double z = player.getZ() - cos * back - sin * SIDE_OFFSET;
        double y = player.getY() + HEIGHT_OFFSET + Math.sin(player.age * 0.16D) * HOVER_AMPLITUDE;
        return new Vec3d(x, y, z);
    }

    private static PokemonEntity createVisual(ServerPlayerEntity player, Pokemon pokemon) {
        PokemonProperties properties = canonicalVisualProperties(pokemon);
        PokemonEntity visual = properties.createEntity(player.getServerWorld());
        if (visual == null)
            throw new IllegalStateException("Cobblemon could not create Fusion Stand entity for "
                    + pokemon.getSpecies().getResourceIdentifier());
        visual.setId(standEntityId(player.getId()));
        visual.setUuid(standEntityUuid(player.getUuid()));
        visual.hideNameRendering();
        visual.setEnablePoseTypeRecalculation(false);
        syncStandState(player, visual);
        return visual;
    }

    /** Cobblemon's TRANSFORM extractor preserves formOnlyShowdownId() plus the actual aspect set. */
    private static PokemonProperties canonicalVisualProperties(Pokemon pokemon) {
        PokemonProperties properties = pokemon.createPokemonProperties(PokemonPropertyExtractor.TRANSFORM);
        properties.setShiny(pokemon.getShiny());
        properties.setLevel(pokemon.getLevel());
        properties.setScaleModifier(pokemon.getScaleModifier());
        return properties;
    }

    private static String visualSignature(Pokemon pokemon) {
        return canonicalVisualProperties(pokemon).asString(" ");
    }

    private static void raw(ServerPlayerEntity viewer, Packet<?> packet) {
        if (!(viewer.networkHandler instanceof FusionRawPacketSender sender))
            throw new IllegalStateException("SVFrameMMO Cobblemon Fusion Stand packet mixin is not applied to ServerPlayNetworkHandler");
        sender.svframe$sendRaw(packet);
    }

    private enum AnimationKind { PHYSICAL, SPECIAL, STATUS }
    private record Frame(PoseType pose, boolean moving) { }

    private record Transform(double x, double y, double z, float yaw, float pitch, byte headYaw,
                             double velocityX, double velocityY, double velocityZ, boolean onGround) {
        private static Transform capture(PokemonEntity visual) {
            Vec3d velocity = visual.getVelocity();
            return new Transform(
                    visual.getX(), visual.getY(), visual.getZ(), visual.getYaw(), visual.getPitch(),
                    angle(visual.getHeadYaw()), velocity.x, velocity.y, velocity.z, visual.isOnGround()
            );
        }
    }

    private static final class State {
        private final UUID playerUuid;
        private final UUID pokemonUuid;
        private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
        private PokemonEntity visual;
        private int playerEntityId;
        private net.minecraft.registry.RegistryKey<World> worldKey;
        private String visualSignature;
        private Frame frame;
        private Transform lastTransform;
        private int suppressBasicUntilAge = Integer.MIN_VALUE;
        private int physicalPulseUntilAge = Integer.MIN_VALUE;

        private State(UUID playerUuid, UUID pokemonUuid, PokemonEntity visual, int playerEntityId,
                      net.minecraft.registry.RegistryKey<World> worldKey, String visualSignature, Frame frame) {
            this.playerUuid = playerUuid;
            this.pokemonUuid = pokemonUuid;
            this.visual = visual;
            this.playerEntityId = playerEntityId;
            this.worldKey = worldKey;
            this.visualSignature = visualSignature;
            this.frame = frame;
        }
    }
}
