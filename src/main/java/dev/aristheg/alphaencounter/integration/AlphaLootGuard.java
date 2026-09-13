package dev.aristheg.alphaencounter.integration;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.drop.ItemDropEntry;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.drops.LootDroppedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import dev.aristheg.alphaencounter.runtime.EncounterRuntime;
import net.minecraft.entity.LivingEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Removes Hyper Training / IV candies from Alpha-Encounter Pokemon loot.
 *
 * Alpha-Encounter is a runtime concept, not a Cobblemon Pokemon boolean flag.
 * Every managed Pokemon/entity UUID is therefore remembered for the lifetime of
 * the server process. This also covers the post-defeat catch window and the
 * short ordering gap between Cobblemon loot events and runtime cleanup.
 *
 * The LOOT_DROPPED filter is intentionally LOWEST priority so other addons may
 * finish modifying the drop list first. A second, final enforcement point is
 * installed by ItemDropEntryMixin at ItemDropEntry.drop(), so an addon cannot
 * re-add an IV candy after this event filter and have it actually spawn/give.
 */
public final class AlphaLootGuard {
    private static final Gson GSON = new GsonBuilder().create();

    private static final Set<String> IV_CANDIES = Set.of(
        "cobblemon:health_candy",
        "cobblemon:mighty_candy",
        "cobblemon:tough_candy",
        "cobblemon:smart_candy",
        "cobblemon:courage_candy",
        "cobblemon:quick_candy",
        "cobblemon:sickly_candy",
        "cobblemon:weak_candy",
        "cobblemon:brittle_candy",
        "cobblemon:numb_candy",
        "cobblemon:coward_candy",
        "cobblemon:slow_candy"
    );

    private static final Set<UUID> MANAGED_POKEMON = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> MANAGED_ENTITIES = ConcurrentHashMap.newKeySet();

    private AlphaLootGuard() {}

    public static void initialize() {
        seedFromPersistentState();
        CobblemonEvents.LOOT_DROPPED.subscribe(Priority.LOWEST, AlphaLootGuard::onLootDropped);
        AlphaEncounterMod.LOGGER.info("Alpha loot guard enabled for runtime-managed Alpha Pokemon.");
    }

    private static void onLootDropped(LootDroppedEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemonEntity)) return;
        if (!isManagedAlpha(pokemonEntity)) return;

        int before = event.getDrops().size();
        event.getDrops().removeIf(AlphaLootGuard::isIvCandyDrop);
        int removed = before - event.getDrops().size();
        if (removed > 0) {
            AlphaEncounterMod.LOGGER.debug(
                "Removed {} IV-candy drop entr{} from Alpha-Encounter Pokemon {}.",
                removed,
                removed == 1 ? "y" : "ies",
                pokemonEntity.getPokemon().getUuid()
            );
        }
    }

    /**
     * Final enforcement used by ItemDropEntryMixin immediately before Cobblemon
     * materializes an item drop. This deliberately accepts Object for itemId so
     * it remains mapping-stable across Yarn/Mojmap ResourceLocation remapping.
     */
    public static boolean shouldBlockItemDrop(LivingEntity entity, Object itemId) {
        if (!(entity instanceof PokemonEntity pokemonEntity) || itemId == null) return false;
        if (!IV_CANDIES.contains(itemId.toString())) return false;
        boolean blocked = isManagedAlpha(pokemonEntity);
        if (blocked) {
            AlphaEncounterMod.LOGGER.debug(
                "Blocked IV candy {} at final drop boundary for Alpha-Encounter Pokemon {}.",
                itemId,
                pokemonEntity.getPokemon().getUuid()
            );
        }
        return blocked;
    }

    private static boolean isManagedAlpha(PokemonEntity entity) {
        UUID entityId = entity.getUuid();
        UUID pokemonId = entity.getPokemon().getUuid();
        if (MANAGED_ENTITIES.contains(entityId) || MANAGED_POKEMON.contains(pokemonId)) return true;

        // Defensive fallback for any encounter created before identity tracking initialized.
        for (ActiveEncounter active : AlphaEncounterMod.RUNTIME.activeSorted()) {
            if (entityId.equals(active.entityId) || pokemonId.equals(active.pokemonId)) {
                track(active.entityId, active.pokemonId);
                return true;
            }
        }
        return false;
    }

    private static boolean isIvCandyDrop(Object drop) {
        if (!(drop instanceof ItemDropEntry itemDrop)) return false;
        return itemDrop.getItem() != null && IV_CANDIES.contains(itemDrop.getItem().toString());
    }

    public static void track(UUID entityId, UUID pokemonId) {
        if (entityId != null) MANAGED_ENTITIES.add(entityId);
        if (pokemonId != null) MANAGED_POKEMON.add(pokemonId);
    }

    private static void seedFromPersistentState() {
        Path stateFile = AlphaEncounterMod.CONFIG.stateFile();
        if (Files.notExists(stateFile)) return;
        try {
            EncounterRuntime.SavedState state = GSON.fromJson(Files.readString(stateFile), EncounterRuntime.SavedState.class);
            if (state == null) return;
            state.normalize();
            for (EncounterRuntime.SavedEncounter saved : state.active) {
                track(parseUuid(saved.entityId), parseUuid(saved.pokemonId));
            }
            for (EncounterRuntime.SavedCatchWindow saved : state.catchWindows) {
                track(parseUuid(saved.entityId), parseUuid(saved.pokemonId));
            }
        } catch (Exception e) {
            AlphaEncounterMod.LOGGER.warn(
                "Could not seed Alpha loot identities from persistent state; live encounters will still be tracked.",
                e
            );
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
