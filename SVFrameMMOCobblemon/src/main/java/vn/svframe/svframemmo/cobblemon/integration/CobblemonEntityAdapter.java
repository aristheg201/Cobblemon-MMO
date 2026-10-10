package vn.svframe.svframemmo.cobblemon.integration;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.api.pokemon.status.Statuses;
import com.cobblemon.mod.common.pokemon.status.PersistentStatus;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import java.util.*;

/** Overworld integration only: no effects may mutate formal battle participants or capture state. */
public final class CobblemonEntityAdapter implements RpgEntityAdapters.Adapter {
    public static final CobblemonEntityAdapter INSTANCE=new CobblemonEntityAdapter();
    @Override public boolean supports(LivingEntity target){return target instanceof PokemonEntity;}
    @Override public boolean allowsFrom(Entity actor,RpgEntityAdapters.Effect effect){
        if(!(actor instanceof PokemonEntity entity))return true;
        var pokemon=entity.getPokemon();
        return entity.isAlive()&&!entity.isBattling()&&!entity.isBusy()&&!pokemon.isBattleClone()&&!pokemon.isFainted();
    }
    @Override public boolean allowsFrom(Entity actor,LivingEntity target,RpgEntityAdapters.Effect effect){
        if(!allowsFrom(actor,effect))return false;
        if(effect==RpgEntityAdapters.Effect.HEAL||!(actor instanceof PokemonEntity pokemon))return true;
        UUID owner=pokemon.getPokemon().getOwnerUUID();
        if(owner==null)return true;
        UUID targetOwner=target instanceof ServerPlayerEntity player?player.getUuid():
                target instanceof PokemonEntity other?other.getPokemon().getOwnerUUID():null;
        if(targetOwner==null)return true;
        if(owner.equals(targetOwner))return false;
        var trainer=pokemon.getServer().getPlayerManager().getPlayer(owner);
        var opponent=pokemon.getServer().getPlayerManager().getPlayer(targetOwner);
        return trainer!=null&&opponent!=null&&pokemon.getServer().isPvpEnabled()
                &&!trainer.isTeammate(opponent)&&!opponent.isCreative()&&!opponent.isSpectator();
    }
    @Override public boolean allows(Entity actor,LivingEntity target,RpgEntityAdapters.Effect effect){
        var entity=(PokemonEntity)target;var pokemon=entity.getPokemon();
        if(entity.isBattling()||entity.isBusy()||pokemon.isBattleClone()||pokemon.isFainted())return false;
        UUID owner=pokemon.getOwnerUUID();
        if(effect==RpgEntityAdapters.Effect.HEAL)return owner==null||actor==null||owner.equals(actor.getUuid());
        if(!entity.canTakeDamage())return false;
        if(owner==null)return true;
        if(actor!=null&&owner.equals(actor.getUuid()))return false;
        if(!SVFrameMMOCobblemon.config().combat.allowOwnedPokemonTargets)return false;
        if(actor instanceof ServerPlayerEntity player){
            var trainer=entity.getServer().getPlayerManager().getPlayer(owner);
            return player.getServer().isPvpEnabled()&&trainer!=null&&!trainer.isCreative()&&!trainer.isSpectator()&&!player.isTeammate(trainer);
        }
        return actor!=null;
    }
    @Override public void heal(LivingEntity target,float amount){
        var entity=(PokemonEntity)target;entity.heal(amount);
        var pokemon=entity.getPokemon();
        if(pokemon.getOwnerUUID()!=null&&entity.getMaxHealth()>0)pokemon.setCurrentHealth(Math.min(pokemon.getMaxHealth(),Math.max(1,Math.round(entity.getHealth()/entity.getMaxHealth()*pokemon.getMaxHealth()))));
    }
    public boolean status(Entity actor,PokemonEntity entity,String name){
        if(!RpgEntityAdapters.allows(actor,entity,RpgEntityAdapters.Effect.STATUS)||entity.getPokemon().getStatus()!=null)return false;
        var status=switch(name){case "burn"->Statuses.BURN;case "poison"->Statuses.POISON;case "poisonbadly"->Statuses.POISON_BADLY;case "paralysis"->Statuses.PARALYSIS;case "sleep"->Statuses.SLEEP;case "frozen"->Statuses.FROZEN;default->Statuses.getStatus(name);};
        if(!(status instanceof PersistentStatus persistent))return false;
        for(var type:entity.getPokemon().getForm().getTypes())if(CobblemonTypeChart.immune(type.getName(),name))return false;
        entity.getPokemon().applyStatus(persistent);return true;
    }
    @Override public Map<String,Object> describe(LivingEntity target){
        var entity=(PokemonEntity)target;var pokemon=entity.getPokemon();var out=new LinkedHashMap<String,Object>();
        out.put("entity",entity.getUuid().toString());out.put("pokemon",pokemon.getUuid().toString());out.put("species",pokemon.getSpecies().getResourceIdentifier().toString());
        out.put("form",pokemon.getForm().getName());out.put("aspects",List.copyOf(pokemon.getAspects()));out.put("overworldHealth",entity.getHealth());out.put("overworldMaxHealth",entity.getMaxHealth());
        out.put("pokemonHealth",pokemon.getCurrentHealth());out.put("pokemonMaxHealth",pokemon.getMaxHealth());out.put("owner",String.valueOf(pokemon.getOwnerUUID()));out.put("battle",String.valueOf(entity.getBattleId()));out.put("busy",entity.isBusy());
        return Collections.unmodifiableMap(out);
    }
}
