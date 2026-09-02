package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonPropertyExtractor;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.spawn.SpawnPokemonPacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionRawPacketSender;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-only Fusion disguise runtime.
 *
 * The authoritative gameplay entity is always the real ServerPlayerEntity. Other viewers receive a Cobblemon
 * PokemonEntity under that player's real entity id. The fused player receives a second packet-only PokemonEntity for
 * third-person self view. That puppet never exists in ServerWorld and has no AI, hitbox, inventory or gameplay state.
 * The local player representation is hidden only in packets sent to that same client; ServerPlayerEntity invisibility
 * is never mutated.
 */
public final class FusionVisualBridge {
    private static final int SELF_VIEW_ENTITY_BASE = 1_000_000_000;
    private static final int ENTITY_FLAGS_TRACKER_ID = 0;
    private static final byte FLAG_ON_FIRE = 1 << 0;
    private static final byte FLAG_SNEAKING = 1 << 1;
    private static final byte FLAG_SPRINTING = 1 << 3;
    private static final byte FLAG_SWIMMING = 1 << 4;
    private static final byte FLAG_INVISIBLE = 1 << 5;
    private static final byte FLAG_GLOWING = 1 << 6;
    private static final byte FLAG_FALL_FLYING = (byte) (1 << 7);
    private static final int VISUAL_REFRESH_INTERVAL = 20;
    private static final int SELF_HIDE_REFRESH_INTERVAL = 4;

    private static volatile FusionVisualBridge activeBridge;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FusionVisualBridge() {
        FusionVisualBridge previous = activeBridge;
        if (previous != null && previous != this)
            throw new IllegalStateException("Only one FusionVisualBridge may own packet disguise state");
        activeBridge = this;
    }

    /** Called by the network-handler mixin. Returns true when the original packet was replaced/cancelled. */
    public static boolean rewriteOutgoing(ServerPlayerEntity viewer, Packet<?> packet) {
        FusionVisualBridge bridge = activeBridge;
        return bridge != null && bridge.rewrite(viewer, packet);
    }

    public void start(ServerPlayerEntity player, Pokemon pokemon) {
        UUID playerId = player.getUuid();
        if (states.containsKey(playerId))
            throw new IllegalStateException("Fusion disguise already active for " + playerId);

        PokemonEntity deployed = pokemon.getEntity();
        if (deployed != null && !deployed.isRemoved())
            throw new IllegalStateException("Fusion disguise requires the party Pokemon to be recalled; refusing duplicate Pokemon entity");

        String signature = visualSignature(pokemon);
        PokemonEntity visual = createVisual(player, pokemon);
        Frame frame = syncVisualState(player, visual);
        State state = new State(playerId, pokemon.getUuid(), visual, player.getId(),
                player.getServerWorld().getRegistryKey(), signature, frame);
        if (states.putIfAbsent(playerId, state) != null)
            throw new IllegalStateException("Fusion disguise already active for " + playerId);

        try {
            disguiseCurrentTrackingViewers(player, state);
            spawnSelfView(player, state);
        } catch (RuntimeException error) {
            states.remove(playerId, state);
            try {
                restorePlayerView(player, state);
            } catch (RuntimeException restoreError) {
                error.addSuppressed(restoreError);
            }
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
            Frame current = syncVisualState(player, state.visual);
            state.frame = current;

            if (player.networkHandler.isConnectionOpen()) {
                // The self puppet has no server tracker, so its complete transform is driven explicitly.
                raw(player, new EntityPositionS2CPacket(state.visual));
                raw(player, new EntitySetHeadYawS2CPacket(state.visual, angle(player.getHeadYaw())));
                raw(player, new EntityVelocityUpdateS2CPacket(state.visual.getId(), player.getVelocity()));

                if (!current.equals(previous)) {
                    sendCoreVisualMetadata(player, state.visual.getId(), current);
                    sendCoreVisualMetadataToTracking(player, current);
                }

                // Vanilla/client inventory state can rewrite the local player's visual flags/equipment. Reassert the
                // self-only hiding state without ever mutating ServerPlayerEntity itself.
                if (player.age % SELF_HIDE_REFRESH_INTERVAL == 0) {
                    raw(player, selfFlagsPacket(player, true));
                    raw(player, new EntityEquipmentUpdateS2CPacket(player.getId(), emptyEquipment()));
                }
            }
        }
        return invalid;
    }

    public void stop(ServerPlayerEntity player, UUID playerId) {
        State removed = states.remove(playerId);
        if (removed == null || player == null) return;
        restorePlayerView(player, removed);
    }

    private boolean rewrite(ServerPlayerEntity viewer, Packet<?> packet) {
        if (viewer == null || packet == null || states.isEmpty()) return false;
        State self = states.get(viewer.getUuid());

        if (packet instanceof EntitiesDestroyS2CPacket destroy && self != null) {
            int[] ids = destroy.getEntityIds().toIntArray();
            boolean containsSelf = false;
            int remainingCount = 0;
            for (int id : ids) {
                if (id == viewer.getId()) containsSelf = true;
                else remainingCount++;
            }
            if (containsSelf) {
                if (remainingCount > 0) {
                    int[] remaining = new int[remainingCount];
                    int at = 0;
                    for (int id : ids) if (id != viewer.getId()) remaining[at++] = id;
                    raw(viewer, new EntitiesDestroyS2CPacket(remaining));
                }
                return true;
            }
        }

        if (packet instanceof EntitySpawnS2CPacket spawn) {
            Entity original = viewer.getServerWorld().getEntityById(spawn.getEntityId());
            if (original instanceof ServerPlayerEntity subject) {
                State state = states.get(subject.getUuid());
                if (state != null) {
                    if (subject.getUuid().equals(viewer.getUuid())) return true;
                    Frame frame = syncVisualState(subject, state.visual);
                    state.frame = frame;
                    raw(viewer, pokemonSpawn(subject, state.visual, subject.getId(), subject.getUuid()));
                    sendCoreVisualMetadata(viewer, subject.getId(), frame);
                    raw(viewer, new EntitySetHeadYawS2CPacket(subject, angle(subject.getHeadYaw())));
                    return true;
                }
            }
        }

        if (packet instanceof EntityTrackerUpdateS2CPacket tracked) {
            if (self != null && tracked.id() == viewer.getId()) {
                raw(viewer, new EntityTrackerUpdateS2CPacket(tracked.id(), forceInvisible(tracked.trackedValues(), viewer)));
                return true;
            }

            Entity original = viewer.getServerWorld().getEntityById(tracked.id());
            if (original instanceof ServerPlayerEntity subject && !subject.getUuid().equals(viewer.getUuid())) {
                State state = states.get(subject.getUuid());
                if (state != null) {
                    Frame frame = syncVisualState(subject, state.visual);
                    state.frame = frame;
                    sendCoreVisualMetadata(viewer, subject.getId(), frame);
                    return true;
                }
            }
        }

        if (packet instanceof EntityEquipmentUpdateS2CPacket equipment) {
            if (self != null && equipment.getEntityId() == viewer.getId()) {
                raw(viewer, new EntityEquipmentUpdateS2CPacket(viewer.getId(), emptyEquipment()));
                return true;
            }
            Entity original = viewer.getServerWorld().getEntityById(equipment.getEntityId());
            if (original instanceof ServerPlayerEntity subject && states.containsKey(subject.getUuid())) {
                // Never attach player armor/held items to a Pokemon disguise shown to another viewer.
                return true;
            }
        }

        if (packet instanceof EntityAnimationS2CPacket animation && self != null
                && animation.getEntityId() == viewer.getId()) {
            raw(viewer, new EntityAnimationS2CPacket(self.visual, animation.getAnimationId()));
            return false;
        }

        if (packet instanceof EntityStatusS2CPacket status && self != null) {
            Entity statusEntity = status.getEntity(viewer.getServerWorld());
            if (statusEntity == viewer) {
                raw(viewer, new EntityStatusS2CPacket(self.visual, status.getStatus()));
                return false;
            }
        }

        return false;
    }

    private void respawnVisual(ServerPlayerEntity player, Pokemon pokemon, State state) {
        if (player.networkHandler.isConnectionOpen()) {
            raw(player, new EntitiesDestroyS2CPacket(state.visual.getId()));
            removeSelfTeam(player, state);
        }

        state.visualSignature = visualSignature(pokemon);
        state.visual = createVisual(player, pokemon);
        state.playerEntityId = player.getId();
        state.worldKey = player.getServerWorld().getRegistryKey();
        state.frame = syncVisualState(player, state.visual);

        if (player.networkHandler.isConnectionOpen()) spawnSelfView(player, state);
        disguiseCurrentTrackingViewers(player, state);
    }

    private void disguiseCurrentTrackingViewers(ServerPlayerEntity player, State state) {
        Frame frame = syncVisualState(player, state.visual);
        state.frame = frame;
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (viewer.getUuid().equals(player.getUuid()) || !viewer.networkHandler.isConnectionOpen()) continue;
            raw(viewer, new EntitiesDestroyS2CPacket(player.getId()));
            raw(viewer, pokemonSpawn(player, state.visual, player.getId(), player.getUuid()));
            sendCoreVisualMetadata(viewer, player.getId(), frame);
            raw(viewer, new EntitySetHeadYawS2CPacket(player, angle(player.getHeadYaw())));
            raw(viewer, new EntityVelocityUpdateS2CPacket(player.getId(), player.getVelocity()));
        }
    }

    private void sendCoreVisualMetadataToTracking(ServerPlayerEntity player, Frame frame) {
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (viewer.getUuid().equals(player.getUuid()) || !viewer.networkHandler.isConnectionOpen()) continue;
            sendCoreVisualMetadata(viewer, player.getId(), frame);
        }
    }

    private void spawnSelfView(ServerPlayerEntity player, State state) {
        Frame frame = syncVisualState(player, state.visual);
        state.frame = frame;
        raw(player, new EntitiesDestroyS2CPacket(state.visual.getId()));
        raw(player, pokemonSpawn(player, state.visual, state.visual.getId(), state.visual.getUuid()));
        sendCoreVisualMetadata(player, state.visual.getId(), frame);
        raw(player, new EntitySetHeadYawS2CPacket(state.visual, angle(player.getHeadYaw())));
        raw(player, new EntityVelocityUpdateS2CPacket(state.visual.getId(), player.getVelocity()));
        raw(player, selfFlagsPacket(player, true));
        raw(player, new EntityEquipmentUpdateS2CPacket(player.getId(), emptyEquipment()));
        ensureSelfTeam(player, state);
    }

    private void restorePlayerView(ServerPlayerEntity player, State state) {
        if (player.networkHandler.isConnectionOpen()) {
            raw(player, new EntitiesDestroyS2CPacket(state.visual.getId()));
            removeSelfTeam(player, state);
            raw(player, selfFlagsPacket(player, false));
            raw(player, new EntityEquipmentUpdateS2CPacket(player.getId(), equipment(player)));
            restoreOriginalTeam(player);
            player.playerScreenHandler.syncState();
            if (player.currentScreenHandler != player.playerScreenHandler) player.currentScreenHandler.syncState();
        }

        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (viewer.getUuid().equals(player.getUuid()) || !viewer.networkHandler.isConnectionOpen()) continue;
            raw(viewer, new EntitiesDestroyS2CPacket(player.getId()));
            raw(viewer, realPlayerSpawn(player));
            sendRealPlayerMetadata(viewer, player);
            raw(viewer, new EntityEquipmentUpdateS2CPacket(player.getId(), equipment(player)));
            raw(viewer, new EntitySetHeadYawS2CPacket(player, angle(player.getHeadYaw())));
            raw(viewer, new EntityVelocityUpdateS2CPacket(player.getId(), player.getVelocity()));
        }
    }

    /** Cobblemon's spawn payload supplies species/form/aspects/shiny/scale/pose around the nested vanilla transform. */
    private static CustomPayloadS2CPacket pokemonSpawn(ServerPlayerEntity player, PokemonEntity visual,
                                                       int entityId, UUID entityUuid) {
        EntitySpawnS2CPacket vanilla = new EntitySpawnS2CPacket(
                entityId,
                entityUuid,
                player.getX(), player.getY(), player.getZ(),
                player.getPitch(), player.getYaw(),
                visual.getType(),
                0,
                player.getVelocity(),
                player.getHeadYaw()
        );
        return new CustomPayloadS2CPacket(new SpawnPokemonPacket(visual, vanilla));
    }

    private static EntitySpawnS2CPacket realPlayerSpawn(ServerPlayerEntity player) {
        return new EntitySpawnS2CPacket(
                player.getId(),
                player.getUuid(),
                player.getX(), player.getY(), player.getZ(),
                player.getPitch(), player.getYaw(),
                player.getType(),
                0,
                player.getVelocity(),
                player.getHeadYaw()
        );
    }

    /** Core Cobblemon locomotion state is sent explicitly; getChangedEntries() is intentionally not used here. */
    private static void sendCoreVisualMetadata(ServerPlayerEntity viewer, int entityId, Frame frame) {
        List<DataTracker.SerializedEntry<?>> entries = List.of(
                DataTracker.SerializedEntry.of(PokemonEntity.getMOVING(), frame.moving),
                DataTracker.SerializedEntry.of(PokemonEntity.getPOSE_TYPE(), frame.pose),
                DataTracker.SerializedEntry.of(PokemonEntity.getHIDE_LABEL(), true)
        );
        raw(viewer, new EntityTrackerUpdateS2CPacket(entityId, entries));
    }

    private static void sendRealPlayerMetadata(ServerPlayerEntity viewer, ServerPlayerEntity player) {
        List<DataTracker.SerializedEntry<?>> entries = player.getDataTracker().getChangedEntries();
        if (entries != null && !entries.isEmpty())
            raw(viewer, new EntityTrackerUpdateS2CPacket(player.getId(), List.copyOf(entries)));
        else
            raw(viewer, new EntityTrackerUpdateS2CPacket(player.getId(), List.of(byteEntry(playerFlags(player)))));
    }

    private static EntityTrackerUpdateS2CPacket selfFlagsPacket(ServerPlayerEntity player, boolean forceInvisible) {
        byte flags = playerFlags(player);
        if (forceInvisible) flags |= FLAG_INVISIBLE;
        else flags &= (byte) ~FLAG_INVISIBLE;
        if (!forceInvisible && player.isInvisible()) flags |= FLAG_INVISIBLE;
        return new EntityTrackerUpdateS2CPacket(player.getId(), List.of(byteEntry(flags)));
    }

    private static List<DataTracker.SerializedEntry<?>> forceInvisible(List<DataTracker.SerializedEntry<?>> source,
                                                                        ServerPlayerEntity player) {
        List<DataTracker.SerializedEntry<?>> result = new ArrayList<>(source.size() + 1);
        boolean replacedFlags = false;
        for (DataTracker.SerializedEntry<?> entry : source) {
            if (entry.id() == ENTITY_FLAGS_TRACKER_ID && entry.value() instanceof Byte value) {
                result.add(byteEntry((byte) (value | FLAG_INVISIBLE)));
                replacedFlags = true;
            } else {
                result.add(entry);
            }
        }
        if (!replacedFlags) result.add(byteEntry((byte) (playerFlags(player) | FLAG_INVISIBLE)));
        return List.copyOf(result);
    }

    private static DataTracker.SerializedEntry<Byte> byteEntry(byte value) {
        return new DataTracker.SerializedEntry<>(ENTITY_FLAGS_TRACKER_ID, TrackedDataHandlerRegistry.BYTE, value);
    }

    private static byte playerFlags(ServerPlayerEntity player) {
        byte flags = 0;
        if (player.isOnFire()) flags |= FLAG_ON_FIRE;
        if (player.isSneaking()) flags |= FLAG_SNEAKING;
        if (player.isSprinting()) flags |= FLAG_SPRINTING;
        if (player.isSwimming()) flags |= FLAG_SWIMMING;
        if (player.isInvisible()) flags |= FLAG_INVISIBLE;
        if (player.isGlowing()) flags |= FLAG_GLOWING;
        if (player.isFallFlying()) flags |= FLAG_FALL_FLYING;
        return flags;
    }

    private static List<Pair<EquipmentSlot, ItemStack>> equipment(ServerPlayerEntity player) {
        return Arrays.stream(EquipmentSlot.values())
                .map(slot -> Pair.of(slot, player.getEquippedStack(slot).copy()))
                .toList();
    }

    private static List<Pair<EquipmentSlot, ItemStack>> emptyEquipment() {
        return Arrays.stream(EquipmentSlot.values())
                .map(slot -> Pair.of(slot, ItemStack.EMPTY))
                .toList();
    }

    private static byte angle(float degrees) {
        return (byte) ((int) (degrees * 256.0F / 360.0F));
    }

    private static int selfViewEntityId(int playerEntityId) {
        if (playerEntityId < 0 || playerEntityId > Integer.MAX_VALUE - SELF_VIEW_ENTITY_BASE)
            throw new IllegalStateException("Player entity id cannot be mapped to a Fusion self-view id: " + playerEntityId);
        return SELF_VIEW_ENTITY_BASE + playerEntityId;
    }

    private static UUID selfViewEntityUuid(UUID playerUuid) {
        return UUID.nameUUIDFromBytes(("svframemmo_cobblemon:fusion-self:" + playerUuid)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static Frame syncVisualState(ServerPlayerEntity player, PokemonEntity visual) {
        visual.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        visual.setVelocity(player.getVelocity());
        visual.setOnGround(player.isOnGround());
        visual.setBodyYaw(player.bodyYaw);
        visual.setHeadYaw(player.headYaw);
        visual.setSprinting(player.isSprinting());
        visual.setSneaking(player.isSneaking());
        visual.setSwimming(player.isSwimming());
        visual.setGlowing(player.isGlowing());
        visual.setInvisible(false);
        visual.setOnFire(player.isOnFire());

        Vec3d velocity = player.getVelocity();
        boolean moving = velocity.horizontalLengthSquared() > 1.0e-4
                || Math.abs(velocity.y) > 0.04
                || player.isSprinting();
        boolean water = player.isTouchingWater() || player.isSubmergedInWater();

        PoseType pose;
        if (player.isSleeping()) {
            pose = PoseType.SLEEP;
            moving = false;
        } else if (water) {
            pose = moving ? PoseType.SWIM : PoseType.FLOAT;
        } else if (player.isFallFlying() && visual.canFly()) {
            pose = PoseType.GLIDE;
            moving = true;
        } else if (!player.isOnGround() && visual.canFly()) {
            pose = moving ? PoseType.FLY : PoseType.HOVER;
        } else {
            pose = moving ? PoseType.WALK : PoseType.STAND;
        }

        visual.getDataTracker().set(PokemonEntity.getMOVING(), moving);
        visual.getDataTracker().set(PokemonEntity.getPOSE_TYPE(), pose);
        visual.getDataTracker().set(PokemonEntity.getHIDE_LABEL(), true);
        visual.setPokemonWalking(pose == PoseType.WALK);
        visual.setPokemonFlying(pose == PoseType.FLY || pose == PoseType.HOVER || pose == PoseType.GLIDE);
        return new Frame(pose, moving);
    }

    private static PokemonEntity createVisual(ServerPlayerEntity player, Pokemon pokemon) {
        PokemonProperties properties = canonicalVisualProperties(pokemon);
        PokemonEntity visual = properties.createEntity(player.getServerWorld());
        if (visual == null)
            throw new IllegalStateException("Cobblemon could not create fusion disguise entity for "
                    + pokemon.getSpecies().getResourceIdentifier());
        visual.setId(selfViewEntityId(player.getId()));
        visual.setUuid(selfViewEntityUuid(player.getUuid()));
        visual.hideNameRendering();
        visual.setEnablePoseTypeRecalculation(false);
        syncVisualState(player, visual);
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

    private static void ensureSelfTeam(ServerPlayerEntity player, State state) {
        if (state.selfTeam != null) return;
        String teamName = "svfus" + Integer.toUnsignedString(player.getId(), 36);
        Team team = new Team(new Scoreboard(), teamName);
        team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
        team.setNameTagVisibilityRule(AbstractTeam.VisibilityRule.NEVER);
        team.setShowFriendlyInvisibles(false);
        team.getPlayerList().add(player.getNameForScoreboard());
        team.getPlayerList().add(state.visual.getNameForScoreboard());
        state.selfTeam = team;
        raw(player, TeamS2CPacket.updateTeam(team, true));
    }

    private static void removeSelfTeam(ServerPlayerEntity player, State state) {
        Team team = state.selfTeam;
        if (team == null) return;
        raw(player, TeamS2CPacket.updateRemovedTeam(team));
        state.selfTeam = null;
    }

    private static void restoreOriginalTeam(ServerPlayerEntity player) {
        Team original = player.getScoreboardTeam();
        if (original != null) {
            raw(player, TeamS2CPacket.changePlayerTeam(
                    original, player.getNameForScoreboard(), TeamS2CPacket.Operation.ADD));
        }
    }

    private static void raw(ServerPlayerEntity viewer, Packet<?> packet) {
        if (!(viewer.networkHandler instanceof FusionRawPacketSender sender))
            throw new IllegalStateException("SVFrameMMO Cobblemon packet-disguise mixin is not applied to ServerPlayNetworkHandler");
        sender.svframe$sendRaw(packet);
    }

    private record Frame(PoseType pose, boolean moving) { }

    private static final class State {
        private final UUID playerUuid;
        private final UUID pokemonUuid;
        private PokemonEntity visual;
        private int playerEntityId;
        private net.minecraft.registry.RegistryKey<World> worldKey;
        private String visualSignature;
        private Frame frame;
        private Team selfTeam;

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
