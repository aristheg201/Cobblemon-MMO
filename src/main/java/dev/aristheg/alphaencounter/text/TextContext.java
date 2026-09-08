package dev.aristheg.alphaencounter.text;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.aristheg.alphaencounter.config.model.EncounterDefinition;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public record TextContext(MinecraftServer server, ServerPlayerEntity player, ActiveEncounter encounter, EncounterDefinition definition, Pokemon pokemon, PokemonEntity entity, String biome) {}
