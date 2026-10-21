package vn.svframe.svframemmo.cobblemon.gameplay;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.actor.PokemonBattleActor;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.api.SVFrameMMOApi;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Awards the selected class through the native MMO curve/level/skill-point pipeline. */
public final class PokemonProgression {
  private static final PokemonRewardLedger LEDGER = new PokemonRewardLedger();

  private PokemonProgression() {}

  public static void register() {
    CobblemonEvents.BATTLE_VICTORY.subscribe(PokemonProgression::victory);
    ServerLivingEntityEvents.AFTER_DEATH.register(
        (entity, source) -> {
          if (entity instanceof PokemonEntity pokemon) defeated(pokemon, source);
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEDGER.clear());
  }

  public static boolean excluded(PokemonEntity entity) {
    return entity.getCommandTags().contains("svframe_worldboss")
        || entity.getCommandTags().contains("svframe_no_exp")
        || entity.getCommandTags().contains("svframe_qa");
  }

  public static void victory(BattleVictoryEvent event) {
    if (!SVFrameMMOCobblemon.config().gameplay.experienceEnabled
        || event.getWasWildCapture()
        || event.getLosers().isEmpty()) return;
    int levels = 0;
    Set<UUID> defeated = new HashSet<>();
    for (var actor : event.getLosers()) {
      // Friendly duels and trainer battles cannot be farmed for class EXP.
      if (!(actor instanceof PokemonBattleActor)) return;
      for (var battler : actor.getPokemonList()) {
        var pokemon = battler.getOriginalPokemon();
        var entity = pokemon.getEntity();
        if (pokemon.getOwnerUUID() != null
            || pokemon.isBattleClone()
            || (entity != null && excluded(entity))) return;
        if (defeated.add(pokemon.getUuid())) levels += pokemon.getLevel();
      }
    }
    if (defeated.isEmpty()) return;
    for (UUID pokemon : defeated) LEDGER.markBattlePokemon(pokemon);
    Set<UUID> trainers = new HashSet<>();
    for (var actor : event.getWinners())
      if (actor instanceof PlayerBattleActor playerActor) {
        var player = playerActor.getEntity();
        if (player != null
            && trainers.add(player.getUuid())
            && LEDGER.claimVictory(event.getBattle().getBattleId(), player.getUuid()))
          reward(
              player,
              SVFrameMMOCobblemon.config().gameplay.battle.amount(levels),
              "battle",
              event.getBattle().getBattleId());
      }
  }

  public static void defeated(PokemonEntity entity, DamageSource source) {
    if (!SVFrameMMOCobblemon.config().gameplay.experienceEnabled
        || excluded(entity)
        || entity.isBattling()
        || entity.isBusy()) return;
    var pokemon = entity.getPokemon();
    if (pokemon.getOwnerUUID() != null || pokemon.isBattleClone()) return;
    ServerPlayerEntity player = trainer(source.getAttacker());
    if (player == null
        || player.isCreative()
        || player.isSpectator()
        || !LEDGER.claimDefeat(pokemon.getUuid())) return;
    reward(
        player,
        SVFrameMMOCobblemon.config().gameplay.overworld.amount(pokemon.getLevel()),
        "overworld",
        pokemon.getUuid());
  }

  public static ServerPlayerEntity trainer(Entity actor) {
    if (actor instanceof ServerPlayerEntity player) return player;
    if (actor instanceof PokemonEntity pokemon
        && pokemon.getPokemon().getOwnerUUID() != null
        && pokemon.getServer() != null)
      return pokemon.getServer().getPlayerManager().getPlayer(pokemon.getPokemon().getOwnerUUID());
    return null;
  }

  private static void reward(ServerPlayerEntity player, double amount, String kind, UUID event) {
    if (amount <= 0) return;
    SVFrameMMOApi.giveExperience(player, amount);
    if ("1".equals(System.getenv("SVFRAME_RUNTIME_QA")))
      SVFrameMMOCobblemon.LOG.info(
          "Pokemon class EXP: player={}, event={}, kind={}, amount={}",
          player.getUuid(),
          event,
          kind,
          amount);
  }
}
