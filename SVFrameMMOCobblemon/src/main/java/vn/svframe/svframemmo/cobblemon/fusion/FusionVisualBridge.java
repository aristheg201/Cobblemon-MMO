package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionRawPacketSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native server-side packet disguise for Fusion.
 *
 * The authoritative entity remains ServerPlayerEntity. The PokemonEntity kept here is only a packet/template entity:
 * it is never added to ServerWorld. Other clients receive the Pokemon type under the real player's entity id, while
 * the fused player receives a separate packet-only self-view Pokemon entity and a client-only invisibility flag for
 * their real player model. Server gameplay, hit registration, inventory, permissions and skills stay on the player.
 */
public final class FusionVisualBridge {
    private static final int ENTITY_FLAGS_TRACKER_ID = 0;
    private static final byte FLAG_ON_FIRE = 1 << 0;
    private static final byte FLAG_SNEAKING = 1 << 1;
    private static final byte FLAG_SPRINTING = 1 << 3;
    private static final byte FLAG_SWIMMING = 1 << 4;
    private static final byte FLAG_INVISIBLE = 1 << 5;
    private static final byte FLAG_GLOWING = 1 << 6;
    private static final byte FLAG_FALL_FLYING = (byte) (1 << 7);

    private static volatile FusionVisualBridge activeBridge;

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public FusionVisualBridge() {
        FusionVisualBridge previous = activeBridge;
        if (previous != null && previous != this)
            throw new IllegalStateException("Only one FusionVisualBridge may own packet disguise state");
        activeBridge = this;
    }

    /** Called by the common network-handler mixin. Returns true when the original packet was replaced/cancelled. */
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

        PokemonEntity visual = createVisual(player, pokemon);
        State state = new State(playerId, pokemon.getUuid(), visual, player.getId(), player.getServerWorld().getRegistryKey());
        if (states.putIfAbsent(playerId, state) != null)
            throw new IllegalStateException("Fusion disguise already active for " + playerId);

        try {
            syncVisualState(player, state.visual);
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
            if (worldChanged) {
                if (player.networkHandler.isConnectionOpen())
                    raw(player, new EntitiesDestroyS2CPacket(state.visual.getId()));
                state.visual = createVisual(player, pokemon);
                state.playerEntityId = player.getId();
                state.worldKey = player.getServerWorld().getRegistryKey();
                if (player.networkHandler.isConnectionOpen()) spawnSelfView(player, state);
            }

            syncVisualState(player, state.visual);
            if (player.networkHandler.isConnectionOpen()) {
                raw(player, new EntityPositionS2CPacket(state.visual));
                raw(player, new EntitySetHeadYawS2CPacket(state.visual, angle(player.getHeadYaw())));
                raw(player, new EntityVelocityUpdateS2CPacket(state.visual.getId(), player.getVelocity()));

                // Keep the local real player hidden without mutating ServerPlayerEntity invisibility state, and keep
                // the packet-only Pokemon's changing base flags/pose data current for F5/self-view.
                if ((player.age & 3) == 0) {
                    sendVisualMetadata(player, state.visual, state.visual.getId());
                    raw(player, selfFlagsPacket(player, true));
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
                    syncVisualState(subject, state.visual);
                    raw(viewer, disguiseSpawn(subject, state.visual, subject.getId(), subject.getUuid()));
                    sendVisualMetadata(viewer, state.visual, subject.getId());
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
                    syncVisualState(subject, state.visual);
                    sendVisualMetadata(viewer, state.visual, subject.getId());
                    return true;
                }
            }
        }

        return false;
    }

    private void disguiseCurrentTrackingViewers(ServerPlayerEntity player, State state) {
        for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
            if (viewer.getUuid().equals(player.getUuid()) || !viewer.networkHandler.isConnectionOpen()) continue;
            raw(viewer, new EntitiesDestroyS2CPacket(player.getId()));
            raw(viewer, disguiseSpawn(player, state.visual, player.getId(), player.getUuid()));
            sendVisualMetadata(viewer, state.visual, player.getId());
            raw(viewer, new EntitySetHeadYawS2CPacket(player, angle(player.getHeadYaw())));
        }
    }

    private void spawnSelfView(ServerPlayerEntity player, State state) {
        syncVisualState(player, state.visual);
        raw(player, new EntitiesDestroyS2CPacket(state.visual.getId()));
        raw(player, disguiseSpawn(player, state.visual, state.visual.getId(), state.visual.getUuid()));
        sendVisualMetadata(player, state.visual, state.visual.getId());
        raw(player, new EntitySetHeadYawS2CPacket(state.visual, angle(player.getHeadYaw())));
        raw(player, new EntityVelocityUpdateS2CPacket(state.visual.getId(), player.getVelocity()));
        raw(player, selfFlagsPacket(player, true));
    }

    private void restorePlayerView(ServerPlayerEntity player, State state) {
        if (player.networkHandler.isConnectionOpen()) {
            raw(player, new EntitiesDestroyS2CPacket(state.visual.getId()));
            raw(player, selfFlagsPacket(player, false));
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

    private static EntitySpawnS2CPacket disguiseSpawn(ServerPlayerEntity player, PokemonEntity visual, int entityId, UUID uuid) {
        return new EntitySpawnS2CPacket(
                entityId,
                uuid,
                player.getX(), player.getY(), player.getZ(),
                player.getPitch(), player.getYaw(),
                visual.getType(),
                0,
                player.getVelocity(),
                player.getHeadYaw()
        );
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

    private static void sendVisualMetadata(ServerPlayerEntity viewer, PokemonEntity visual, int entityId) {
        List<DataTracker.SerializedEntry<?>> entries = visual.getDataTracker().getChangedEntries();
        if (entries != null && !entries.isEmpty())
            raw(viewer, new EntityTrackerUpdateS2CPacket(entityId, List.copyOf(entries)));
    }

    private static void sendRealPlayerMetadata(ServerPlayerEntity viewer, ServerPlayerEntity player) {
        List<DataTracker.SerializedEntry<?>> entries = player.getDataTracker().getChangedEntries();
        if (entries != null && !entries.isEmpty())
            raw(viewer, new EntityTrackerUpdateS2CPacket(player.getId(), List.copyOf(entries)));
    }

    private static EntityTrackerUpdateS2CPacket selfFlagsPacket(ServerPlayerEntity player, boolean forceInvisible) {
        byte flags = playerFlags(player);
        if (forceInvisible) flags |= FLAG_INVISIBLE;
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

    private static byte angle(float degrees) {
        return (byte) ((int) (degrees * 256.0F / 360.0F));
    }

    private static void syncVisualState(ServerPlayerEntity player, PokemonEntity visual) {
        visual.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        visual.setVelocity(player.getVelocity());
        visual.setOnGround(player.isOnGround());
        visual.setBodyYaw(player.bodyYaw);
        visual.setHeadYaw(player.headYaw);
        visual.setSprinting(player.isSprinting());
        visual.setSneaking(player.isSneaking());
        visual.setSwimming(player.isSwimming());
        visual.setGlowing(player.isGlowing());
        visual.setInvisible(player.isInvisible());
        visual.setOnFire(player.isOnFire());
        visual.setNoGravity(true);
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
        visual.hideNameRendering();
        visual.setNoGravity(true);
        syncVisualState(player, visual);
        return visual;
    }

    private static void raw(ServerPlayerEntity viewer, Packet<?> packet) {
        if (!(viewer.networkHandler instanceof FusionRawPacketSender sender))
            throw new IllegalStateException("SVFrameMMO Cobblemon packet-disguise mixin is not applied to ServerPlayNetworkHandler");
        sender.svframe$sendRaw(packet);
    }

    private static final class State {
        private final UUID playerUuid;
        private final UUID pokemonUuid;
        private PokemonEntity visual;
        private int playerEntityId;
        private net.minecraft.registry.RegistryKey<World> worldKey;

        private State(UUID playerUuid, UUID pokemonUuid, PokemonEntity visual, int playerEntityId,
                      net.minecraft.registry.RegistryKey<World> worldKey) {
            this.playerUuid = playerUuid;
            this.pokemonUuid = pokemonUuid;
            this.visual = visual;
            this.playerEntityId = playerEntityId;
            this.worldKey = worldKey;
        }
    }
}
