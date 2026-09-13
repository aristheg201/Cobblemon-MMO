package dev.aristheg.alphaencounter.integration;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.drops.LootDroppedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.config.model.DropFilterConfig;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AlphaDropPolicy {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, String> BY_POKEMON = new ConcurrentHashMap<>();
    private static final Map<UUID, String> BY_ENTITY = new ConcurrentHashMap<>();
    private static final double NATIVE_ALPHA_CALLBACK_MATCH_DISTANCE_SQUARED = 4.0D;
    private static volatile DropFilterConfig filter = defaults();

    private AlphaDropPolicy() {}

    static void initialize() {
        reload();
        CobblemonEvents.LOOT_DROPPED.subscribe(Priority.LOWEST, AlphaDropPolicy::onLoot);
    }

    static synchronized void reload() {
        Path file = AlphaEncounterMod.CONFIG.root().resolve("drop-filter.json");
        try {
            Files.createDirectories(file.getParent());
            DropFilterConfig loaded = Files.exists(file)
                ? GSON.fromJson(Files.readString(file), DropFilterConfig.class)
                : defaults();
            if (loaded == null) loaded = defaults();
            loaded.normalize();
            if (Files.notExists(file)) Files.writeString(file, GSON.toJson(loaded));
            filter = loaded;
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.error("Could not load Alpha drop filter {}.", file, e);
        }
    }

    static void track(String definitionId, UUID entityId, UUID pokemonId) {
        if (definitionId == null || definitionId.isBlank()) return;
        if (entityId != null) BY_ENTITY.put(entityId, definitionId);
        if (pokemonId != null) BY_POKEMON.put(pokemonId, definitionId);
    }

    static boolean shouldFilter(LivingEntity entity, Object itemId) {
        if (!(entity instanceof PokemonEntity pokemon) || itemId == null) return false;
        String definitionId = definitionId(pokemon);
        return definitionId != null && filter.blocks(definitionId, itemId.toString());
    }

    /**
     * Cobblemon 1.8 native Alpha rewards are not Pokemon DropTable drops. The
     * battle_fainted Molang callback calls world.spawn_loot_table_items at the
     * fainted Pokemon's exact coordinates. Resolve that callback back to the
     * managed encounter and apply the same data-driven item policy.
     */
    static boolean shouldFilterNativeAlphaLoot(ServerWorld world, double x, double y, double z, Object itemId) {
        if (world == null || itemId == null) return false;
        String definitionId = definitionIdAt(world, x, y, z);
        return definitionId != null && filter.blocks(definitionId, itemId.toString());
    }

    private static void onLoot(LootDroppedEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemon)) return;
        String definitionId = definitionId(pokemon);
        if (definitionId == null) return;
        event.getDrops().removeIf(drop -> drop instanceof ItemDropEntry item
            && item.getItem() != null
            && filter.blocks(definitionId, item.getItem().toString()));
    }

    private static String definitionId(PokemonEntity entity) {
        String id = BY_ENTITY.get(entity.getUuid());
        if (id == null) id = BY_POKEMON.get(entity.getPokemon().getUuid());
        if (id != null) return id;
        for (ActiveEncounter active : AlphaEncounterMod.RUNTIME.activeSorted()) {
            if (entity.getUuid().equals(active.entityId) || entity.getPokemon().getUuid().equals(active.pokemonId)) {
                track(active.definitionId, active.entityId, active.pokemonId);
                return active.definitionId;
            }
        }
        return null;
    }

    private static String definitionIdAt(ServerWorld world, double x, double y, double z) {
        String dimension = world.getRegistryKey().getValue().toString();
        ActiveEncounter nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (ActiveEncounter active : AlphaEncounterMod.RUNTIME.activeSorted()) {
            double ax;
            double ay;
            double az;

            PokemonEntity entity = active.entityRef;
            if (entity != null && entity.getWorld() == world) {
                ax = entity.getX();
                ay = entity.getY();
                az = entity.getZ();
            } else {
                if (!dimension.equals(active.dimension)) continue;
                ax = active.x;
                ay = active.y;
                az = active.z;
            }

            double dx = ax - x;
            double dy = ay - y;
            double dz = az - z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < nearestDistanceSquared) {
                nearest = active;
                nearestDistanceSquared = distanceSquared;
            }
        }

        if (nearest == null || nearestDistanceSquared > NATIVE_ALPHA_CALLBACK_MATCH_DISTANCE_SQUARED) return null;
        track(nearest.definitionId, nearest.entityId, nearest.pokemonId);
        return nearest.definitionId;
    }

    private static DropFilterConfig defaults() {
        DropFilterConfig config = new DropFilterConfig();
        config.normalize();
        return config;
    }
}
