package dev.aristheg.alphaencounter.integration;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.drops.LootDroppedEvent;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import dev.aristheg.alphaencounter.AlphaEncounterMod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Removes Hyper Training / IV candies from Alpha Pokemon loot without touching
 * normal Pokemon drops or the items themselves. This deliberately covers both
 * additive and subtractive IV candies so Alpha farming cannot bypass stat
 * progression in either direction.
 */
public final class AlphaLootGuard {
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

    private static boolean reflectionWarningLogged;

    private AlphaLootGuard() {}

    public static void initialize() {
        CobblemonEvents.LOOT_DROPPED.subscribe(AlphaLootGuard::onLootDropped);
        AlphaEncounterMod.LOGGER.info("Alpha loot guard enabled: IV candies are blocked for Alpha Pokemon.");
    }

    private static void onLootDropped(LootDroppedEvent event) {
        if (!(event.getEntity() instanceof PokemonEntity pokemonEntity) || !isAlpha(pokemonEntity)) return;

        int before = event.getDrops().size();
        event.getDrops().removeIf(AlphaLootGuard::isIvCandyDrop);
        int removed = before - event.getDrops().size();
        if (removed > 0) {
            AlphaEncounterMod.LOGGER.debug("Removed {} IV-candy drop entr{} from Alpha Pokemon {}.",
                    removed, removed == 1 ? "y" : "ies", pokemonEntity.getPokemon().getUuid());
        }
    }

    private static boolean isAlpha(PokemonEntity pokemonEntity) {
        Object pokemon = pokemonEntity.getPokemon();
        for (String getter : new String[]{"isAlpha", "getIsAlpha"}) {
            try {
                Method method = pokemon.getClass().getMethod(getter);
                return Boolean.TRUE.equals(method.invoke(pokemon));
            } catch (NoSuchMethodException ignored) {
                // Try the other Kotlin/Java accessor spelling.
            } catch (ReflectiveOperationException error) {
                warnReflection(error);
                return false;
            }
        }
        // Defensive fallback in case mappings expose the backing field instead.
        try {
            Field field = pokemon.getClass().getDeclaredField("isAlpha");
            field.setAccessible(true);
            return Boolean.TRUE.equals(field.get(pokemon));
        } catch (ReflectiveOperationException error) {
            warnReflection(error);
            return false;
        }
    }

    private static boolean isIvCandyDrop(Object drop) {
        Object item = invokeNoArg(drop, "getItem");
        if (item == null) item = readField(drop, "item");
        return item != null && IV_CANDIES.contains(item.toString());
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) {
            return null;
        }
    }

    private static void warnReflection(Throwable error) {
        if (reflectionWarningLogged) return;
        reflectionWarningLogged = true;
        AlphaEncounterMod.LOGGER.warn("Could not inspect Cobblemon Alpha flag; IV-candy guard will fail open instead of breaking loot.", error);
    }
}
