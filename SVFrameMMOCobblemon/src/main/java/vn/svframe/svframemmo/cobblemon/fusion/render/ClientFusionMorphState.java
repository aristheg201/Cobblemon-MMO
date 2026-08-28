package vn.svframe.svframemmo.cobblemon.fusion.render;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only render cache. Surrogates are never inserted into ClientWorld and therefore have no gameplay entity. */
public final class ClientFusionMorphState {
    private static final Map<UUID, Entry> MORPHS = new HashMap<>();

    private ClientFusionMorphState() { }

    public static void apply(FusionMorphPayload payload) {
        if (!payload.active()) {
            MORPHS.remove(payload.playerUuid());
            return;
        }
        MORPHS.put(payload.playerUuid(), new Entry(payload.properties()));
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
            entry.entity = entity;
            entry.world = world;
            entry.lastPlayerAge = Integer.MIN_VALUE;
        }

        PokemonEntity entity = entry.entity;
        entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        entity.setVelocity(player.getVelocity());
        entity.setOnGround(player.isOnGround());
        entity.setBodyYaw(player.bodyYaw);
        entity.setHeadYaw(player.headYaw);

        if (entry.lastPlayerAge != player.age) {
            entry.lastPlayerAge = player.age;
            try { entity.tick(); }
            catch (RuntimeException ignored) { /* render surrogate must never break the client tick */ }
            entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
            entity.setBodyYaw(player.bodyYaw);
            entity.setHeadYaw(player.headYaw);
        }
        return entity;
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
