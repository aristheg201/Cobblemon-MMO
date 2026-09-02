package vn.svframe.svframemmo.cobblemon.fusion.render;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-only fusion render cache. The surrogate PokemonEntity is never inserted into ClientWorld; it exists solely
 * as Cobblemon renderer state while the authoritative gameplay entity remains the real player.
 */
public final class ClientFusionMorphState {
    private static final Map<UUID, Entry> MORPHS = new HashMap<>();

    private ClientFusionMorphState() { }

    public static void apply(FusionMorphPayload payload) {
        if (!payload.active()) {
            MORPHS.remove(payload.playerUuid());
            return;
        }
        Entry current = MORPHS.get(payload.playerUuid());
        if (current == null || !current.properties.equals(payload.properties())) {
            MORPHS.put(payload.playerUuid(), new Entry(payload.properties()));
        }
    }

    public static void clear() {
        MORPHS.clear();
    }

    public static PokemonEntity renderable(AbstractClientPlayerEntity player) {
        Entry entry = MORPHS.get(player.getUuid());
        if (entry == null) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) return null;

        if (entry.entity == null || entry.world != world) {
            PokemonProperties properties = PokemonProperties.Companion.parse(entry.properties);
            PokemonEntity entity = properties.createEntity(world);
            if (entity == null) return null;
            entity.hideNameRendering();
            entity.setNoGravity(true);
            entity.setEnablePoseTypeRecalculation(false);
            entity.getDataTracker().set(PokemonEntity.getHIDE_LABEL(), true);
            entry.entity = entity;
            entry.world = world;
            entry.lastPlayerAge = Integer.MIN_VALUE;
        }

        PokemonEntity entity = entry.entity;
        sync(player, entity);
        if (entry.lastPlayerAge != player.age) {
            entry.lastPlayerAge = player.age;
            entity.getDelegate().updateAge(player.age);
            try {
                entity.tick();
            } catch (RuntimeException ignored) {
                // A render-only surrogate must never be allowed to break the client tick/render loop.
            }
            sync(player, entity);
        }
        return entity;
    }

    private static void sync(AbstractClientPlayerEntity player, PokemonEntity entity) {
        entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        entity.setVelocity(player.getVelocity());
        entity.setOnGround(player.isOnGround());
        entity.setBodyYaw(player.bodyYaw);
        entity.setHeadYaw(player.headYaw);
        entity.setSprinting(player.isSprinting());
        entity.setSneaking(player.isSneaking());
        entity.setSwimming(player.isSwimming());
        entity.setOnFire(player.isOnFire());
        entity.setGlowing(player.isGlowing());
        entity.setInvisible(false);

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
        } else if (player.isFallFlying() && entity.canFly()) {
            pose = PoseType.GLIDE;
            moving = true;
        } else if (!player.isOnGround() && entity.canFly()) {
            pose = moving ? PoseType.FLY : PoseType.HOVER;
        } else {
            pose = moving ? PoseType.WALK : PoseType.STAND;
        }

        entity.getDataTracker().set(PokemonEntity.getMOVING(), moving);
        entity.getDataTracker().set(PokemonEntity.getPOSE_TYPE(), pose);
        entity.setPokemonWalking(pose == PoseType.WALK);
        entity.setPokemonFlying(pose == PoseType.FLY || pose == PoseType.HOVER || pose == PoseType.GLIDE);
    }

    private static final class Entry {
        private final String properties;
        private PokemonEntity entity;
        private ClientWorld world;
        private int lastPlayerAge = Integer.MIN_VALUE;

        private Entry(String properties) {
            this.properties = properties;
        }
    }
}
