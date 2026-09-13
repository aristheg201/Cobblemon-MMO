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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class AlphaDropPolicy {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, String> BY_POKEMON = new ConcurrentHashMap<>();
    private static final Map<UUID, String> BY_ENTITY = new ConcurrentHashMap<>();
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

    private static DropFilterConfig defaults() {
        DropFilterConfig config = new DropFilterConfig();
        config.normalize();
        return config;
    }
}
