package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.activestate.SentOutState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;

/** Resolves only the exact deployed party Pokemon entity a player interacted with. */
public final class DeployedPartyPokemonResolver {
    public Resolution resolve(ServerPlayerEntity player, PokemonEntity entity) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(entity, "entity");
        Pokemon pokemon = entity.getPokemon();
        if (pokemon == null) return Resolution.rejected("That entity has no Pokemon state.");
        if (!pokemon.belongsTo(player) || !player.getUuid().equals(entity.getOwnerUuid()))
            return Resolution.rejected("You can only fuse with your own party Pokemon.");
        if (!(pokemon.getState() instanceof SentOutState))
            return Resolution.rejected("That Pokemon must be deployed outside its Poke Ball.");
        if (entity.isBattling()) return Resolution.rejected("That Pokemon is currently battling.");

        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        Pokemon partyPokemon = party.get(pokemon.getUuid());
        if (partyPokemon == null || !partyPokemon.getUuid().equals(pokemon.getUuid()))
            return Resolution.rejected("That deployed Pokemon is not in your current party.");
        if (partyPokemon.getEntity() == null || !partyPokemon.getEntity().getUuid().equals(entity.getUuid()))
            return Resolution.rejected("That is not the active deployed entity for this party Pokemon.");
        return new Resolution(entity, partyPokemon, party, null);
    }

    public record Resolution(PokemonEntity entity, Pokemon pokemon, PlayerPartyStore party, String rejection) {
        public static Resolution rejected(String reason) { return new Resolution(null, null, null, reason); }
        public boolean accepted() { return rejection == null; }
    }
}
