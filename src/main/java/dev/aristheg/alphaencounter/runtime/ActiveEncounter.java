package dev.aristheg.alphaencounter.runtime;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.entity.boss.ServerBossBar;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ActiveEncounter {
    public final String definitionId;
    public final String tierId;
    public UUID entityId;
    public final UUID pokemonId;
    public final float healthMultiplier;
    public float maxHp = -1f;
    public float hp = -1f;
    public EncounterState state = EncounterState.IDLE;
    public UUID targetPlayer;
    public UUID pendingBattlePlayer;
    public UUID lastBattlePlayer;
    public long battleAtTick;
    public long pendingSinceTick;
    public long nextReengageTick;
    public long nextFieldAttackTick;
    public long nextPathRefreshTick;
    public long missingSinceTick;
    public long phaseRecoveryAtTick;
    public int lastPokemonHealth = -1;
    public boolean phaseRespawnPending;
    public boolean defeatPending;
    public final Set<UUID> participants = new HashSet<>();
    public String dimension = "minecraft:overworld";
    public double x;
    public double y;
    public double z;
    public transient PokemonEntity entityRef;
    public transient Pokemon pokemonRef;
    public transient ServerBossBar bossBar;
    public transient Set<UUID> bossBarViewers = new HashSet<>();

    public ActiveEncounter(String definitionId, String tierId, UUID entityId, UUID pokemonId, float healthMultiplier) {
        this.definitionId = definitionId;
        this.tierId = tierId;
        this.entityId = entityId;
        this.pokemonId = pokemonId;
        this.healthMultiplier = healthMultiplier;
    }
}
