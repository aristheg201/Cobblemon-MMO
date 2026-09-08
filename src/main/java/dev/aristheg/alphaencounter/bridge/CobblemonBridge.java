package dev.aristheg.alphaencounter.bridge;

import com.cobblemon.mod.common.CobblemonEntities;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

public final class CobblemonBridge {
    private CobblemonBridge() {}

    public static PokemonEntity createEntity(PokemonProperties properties, ServerWorld world) {
        PokemonEntity entity = properties.createEntity(world);
        ensureRequestedAspects(properties, entity.getPokemon());
        entity.getPokemon().updateAspects();
        return entity;
    }

    public static void ensureRequestedAspects(PokemonProperties properties, Pokemon pokemon) {
        Set<String> requested = properties.getAspects();
        if (requested == null || requested.isEmpty()) return;
        Set<String> forced = new HashSet<>(pokemon.getForcedAspects());
        if (forced.addAll(requested)) {
            pokemon.setForcedAspects(forced);
            pokemon.updateAspects();
        }
    }

    public static PokemonEntity respawnCanonical(Pokemon pokemon, ServerWorld world, BlockPos pos) {
        PokemonEntity entity = new PokemonEntity(world, pokemon, CobblemonEntities.POKEMON);
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        entity.setQueuedToDespawn(false);
        if (!world.spawnEntity(entity)) return null;
        return entity;
    }

    public static boolean startPve(ServerPlayerEntity player, PokemonEntity pokemon) {
        try {
            BattleBuilder.INSTANCE.pve(player, pokemon);
            return true;
        } catch (Throwable t) {
            AlphaEncounterMod.LOGGER.error("Could not start Cobblemon PVE battle.", t);
            return false;
        }
    }

    public static boolean playAnimation(PokemonEntity entity, String animation) {
        if (entity == null || animation == null || animation.isBlank()) return false;
        try {
            entity.playAnimation(animation, java.util.List.of());
            return true;
        } catch (Throwable t) {
            AlphaEncounterMod.LOGGER.debug("Could not play animation '{}' on {}.", animation, entity.getUuid(), t);
            return false;
        }
    }
}
