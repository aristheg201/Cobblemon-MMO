package dev.aristheg.alphaencounter.runtime;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.boss.ServerBossBar;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveEncounter {
    public final String definitionId; public final String tierId; public UUID entityId; public final UUID pokemonId; public final float healthMultiplier;
    public float maxHp=-1f,hp=-1f; public EncounterState state=EncounterState.IDLE;
    public UUID targetPlayer,pendingBattlePlayer,lastBattlePlayer; public long battleAtTick,pendingSinceTick,nextReengageTick,nextFieldAttackTick,nextPathRefreshTick,missingSinceTick;
    public int lastPokemonHealth=-1,battleLocalMax=-1; public boolean defeatPending;
    public final Set<UUID> participants=new HashSet<>(); public String dimension="minecraft:overworld"; public double x,y,z;
    public transient PokemonEntity entityRef; public transient Pokemon pokemonRef; public transient ServerBossBar bossBar; public transient Set<UUID> bossBarViewers=new HashSet<>();
    public ActiveEncounter(String definitionId,String tierId,UUID entityId,UUID pokemonId,float healthMultiplier){this.definitionId=definitionId;this.tierId=tierId;this.entityId=entityId;this.pokemonId=pokemonId;this.healthMultiplier=healthMultiplier;}
}
