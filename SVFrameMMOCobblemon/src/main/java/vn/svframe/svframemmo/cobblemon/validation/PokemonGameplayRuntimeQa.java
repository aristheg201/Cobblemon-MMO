package vn.svframe.svframemmo.cobblemon.validation;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerLevelChangeEvent;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.gameplay.*;

/**
 * Explicit native probes, at the player's safe arena. Commands require operator and QA environment.
 */
public final class PokemonGameplayRuntimeQa {
  private PokemonGameplayRuntimeQa() {}

  public static int run(ServerPlayerEntity player, String mode) {
    if (!"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))) return 0;
    var report = new LinkedHashMap<String, Object>();
    report.put("player", player.getUuid().toString());
    report.put("class", SVFrameMMO.playerData().get(player).getClassId());
    report.put(
        "cobblemon",
        FabricLoader.getInstance()
            .getModContainer("cobblemon")
            .orElseThrow()
            .getMetadata()
            .getVersion()
            .getFriendlyString());
    report.put(
        "fightorflight",
        FabricLoader.getInstance()
            .getModContainer("fightorflight")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse("absent"));
    switch (mode) {
      case "experience" -> experience(player, report);
      case "hostility" -> hostility(player, report);
      case "rpg" -> rpg(player,report);
      case "temperament" -> temperament(player,report);
      case "battle" -> battle(player, report);
      default -> throw new IllegalArgumentException("Use experience, hostility or battle");
    }
    return 1;
  }

  private static PokemonEntity spawn(
      ServerPlayerEntity player, String properties, double distance) {
    var entity =
        PokemonProperties.Companion.parse(properties).createEntity(player.getServerWorld());
    entity.refreshPositionAndAngles(player.getX() + distance, player.getY(), player.getZ(), 0, 0);
    player.getServerWorld().spawnEntity(entity);
    return entity;
  }

  private static void primeLevel(ServerPlayerEntity player, Map<String, Object> report) {
    var data = SVFrameMMO.playerData().get(player);
    report.put("originalLevel", data.getLevel());
    report.put("originalExperience", data.getExperience());
    if (data.hasReachedMaxLevel())
      throw new IllegalStateException("Select a class below maximum for progression QA");
    long needed = data.getProfess().getExpCurve().getExperience(data, data.getLevel());
    data.setExperience(Math.max(0, needed - 1));
    report.put("levelBefore", data.getLevel());
    report.put("experienceBefore", data.getExperience());
    report.put("experienceToLevel", needed);
  }

  private static void experience(ServerPlayerEntity player, Map<String, Object> report) {
    var oldMode = player.interactionManager.getGameMode();
    player.changeGameMode(GameMode.SURVIVAL);
    primeLevel(player, report);
    var wild = spawn(player, "rattata level=10", 3);
    wild.setAiDisabled(true);
    var source = wild.getDamageSources().playerAttack(player);
    try {
      wild.damage(source, Float.MAX_VALUE);
      var data = SVFrameMMO.playerData().get(player);
      int level = data.getLevel();
      double exp = data.getExperience();
      report.put("levelAfter", level);
      report.put("experienceAfter", exp);
      report.put("actualDeath", wild.isDead());
      report.put("classLeveled", level > ((Number) report.get("levelBefore")).intValue());
      PokemonProgression.defeated(wild, source);
      PokemonProgression.defeated(wild, source);
      report.put("duplicateDeathNoReward", level == data.getLevel() && exp == data.getExperience());
      var boss = spawn(player, "rattata level=10 uncatchable=true", 4);
      boss.addCommandTag("svframe_worldboss");
      boss.setAiDisabled(true);
      boss.damage(source, Float.MAX_VALUE);
      report.put("bossNoOrdinaryExp", level == data.getLevel() && exp == data.getExperience());
      report.put("bossUncatchable", boss.isUncatchable());
      report.put("bossBattleRejected", !boss.canBattle(player) && !boss.forceBattle(player));
      boss.discard();
    } finally {
      wild.discard();
      restoreLevel(player, report);
      player.changeGameMode(oldMode);
      save("experience", report);
    }
  }

  private static void battle(ServerPlayerEntity player, Map<String, Object> report) {
    primeLevel(player, report);
    var party = Cobblemon.INSTANCE.getStorage().getParty(player);
    var starter = PokemonProperties.Companion.parse("charmander level=10 moves=ember").create();
    if (!party.add(starter))
      throw new IllegalStateException("Leave one free party slot for battle QA");
    var opponent = spawn(player, "rattata level=10", 4);
    opponent.addCommandTag("svframe_no_hostility");
    SVFrameLibFabricMod.schedule(30,()->{report.put("canBattle",opponent.canBattle(player));report.put("wildBusy",opponent.isBusy());report.put("wildAlive",opponent.isAlive());report.put("partyBusy",com.cobblemon.mod.common.util.PlayerExtensionsKt.isPartyBusy(player));var start=com.cobblemon.mod.common.battles.BattleBuilder.INSTANCE.pve(player,opponent);report.put("battleStarted",start instanceof com.cobblemon.mod.common.battles.SuccessfulBattleStart);start.ifErrored(error->{var errors=new java.util.ArrayList<String>();for(var item:error.getErrors())errors.add(item.getClass().getSimpleName()+": "+item.getMessageFor(player).getString());report.put("nativeBattleErrors",errors);return kotlin.Unit.INSTANCE;});});
    SVFrameLibFabricMod.schedule(
        60,
        () -> {
          var battle = opponent.getBattle();
          if (battle == null) {
            report.put("error", "Native formal battle did not initialize");
            party.remove(starter);
            opponent.discard();
            restoreLevel(player, report);
            save("battle", report);
            return;
          }
          report.put("battleId", battle.getBattleId().toString());
          report.put("battleBlocksOverworldAI", !PokemonHostility.safe(opponent, player));
          report.put(
              "battleBlocksOverworldDamage", !PokemonOverworldCombat.strike(opponent, player));
          var actor = battle.getActor(player);
          battle.writeShowdownAction(">forcewin " + actor.getShowdownId());
          SVFrameLibFabricMod.schedule(
              100,
              () -> {
                try {
                  var data = SVFrameMMO.playerData().get(player);
                  int level = data.getLevel();
                  double exp = data.getExperience();
                  report.put("ended", battle.getEnded());
                  report.put("nativeWinners", battle.getWinners().size());
                  report.put("levelAfter", level);
                  report.put("experienceAfter", exp);
                  report.put(
                      "classLeveled", level > ((Number) report.get("levelBefore")).intValue());
                  var event =
                      new BattleVictoryEvent(
                          battle, battle.getWinners(), battle.getLosers(), false);
                  PokemonProgression.victory(event);
                  PokemonProgression.victory(event);
                  report.put(
                      "duplicateVictoryNoReward",
                      level == data.getLevel() && exp == data.getExperience());
                } finally {
                  battle.end();
                  party.remove(starter);
                  opponent.discard();
                  restoreLevel(player, report);
                  save("battle", report);
                }
              });
        });
  }

  private static void hostility(ServerPlayerEntity player, Map<String, Object> report) {
    var oldMode = player.interactionManager.getGameMode();
    player.changeGameMode(GameMode.SURVIVAL);
    float originalHealth = player.getHealth();
    player.setHealth(player.getMaxHealth());
    var attacker = spawn(player, "houndoom level=30 moves=tackle", 8);
    var start = attacker.getPos();
    float[] minimum = {player.getHealth()};
    int[] windups = {0};
    report.put("healthBefore", player.getHealth());
    report.put("spawnX", start.x);
    report.put("spawnZ", start.z);
    report.put("claimed", PokemonHostility.claims(attacker));
    for (int sample = 1; sample <= 40; sample++) {
      int index = sample;
      SVFrameLibFabricMod.schedule(
          sample * 5,
          () -> {
            minimum[0] = Math.min(minimum[0], player.getHealth());
            if (PokemonHostility.state(attacker).contains("windup")) windups[0]++;
            if (index == 40) {
              report.put("minimumHealth", minimum[0]);
              report.put(
                  "damagedPlayer", minimum[0] < ((Number) report.get("healthBefore")).floatValue());
              report.put("distanceMoved", attacker.getPos().distanceTo(start));
              report.put("chasedPlayer", attacker.getPos().distanceTo(start) > 1);
              report.put("windupSamples", windups[0]);
              report.put("state", PokemonHostility.state(attacker));
              Object lock = new Object();
              attacker.getBusyLocks().add(lock);
              PokemonHostility.tick(attacker);
              report.put(
                  "busyCancelsPending",
                  !PokemonHostility.safe(attacker, player)
                      && !PokemonOverworldCombat.strike(attacker, player)
                      && !PokemonHostility.state(attacker).contains("windup"));
              attacker.getBusyLocks().remove(lock);
              attacker.setBeamMode(1);
              PokemonHostility.tick(attacker);
              report.put(
                  "captureCancelsPending",
                  !PokemonHostility.safe(attacker, player)
                      && !PokemonOverworldCombat.strike(attacker, player));
              attacker.setBeamMode(0);
              player.changeGameMode(GameMode.CREATIVE);
              report.put("creativeExcluded", !PokemonHostility.safe(attacker, player));
              player.changeGameMode(GameMode.SPECTATOR);
              report.put("spectatorExcluded", !PokemonHostility.safe(attacker, player));
              player.changeGameMode(GameMode.SURVIVAL);
              var party = Cobblemon.INSTANCE.getStorage().getParty(player);
              var owned = PokemonProperties.Companion.parse("rattata level=10").create();
              if (party.add(owned)) {
                var entity =
                    owned.sendOut(
                        player.getServerWorld(),
                        player.getPos().add(3, 0, 0),
                        null,
                        p -> kotlin.Unit.INSTANCE);
                report.put("ownedPokemonExcluded", !PokemonHostility.claims(entity));
                report.put("ownedCannotHitOwner", !PokemonOverworldCombat.strike(entity, player));
                entity.discard();
                party.remove(owned);
              } else report.put("ownedProbe", "not run: party full");
              if (FabricLoader.getInstance().isModLoaded("fightorflight"))
                try {
                  float before = player.getHealth();
                  Object applied =
                      Class.forName("me.rufia.fightorflight.entity.PokemonAttackEffect")
                          .getMethod("pokemonAttack", PokemonEntity.class, Entity.class)
                          .invoke(null, attacker, player);
                  report.put(
                      "fofParallelMeleeRejected",
                      Boolean.FALSE.equals(applied) && before == player.getHealth());
                } catch (ReflectiveOperationException error) {
                  report.put("fofGuardError", error.toString());
                }
              attacker.discard();
              player.setHealth(Math.max(1, Math.min(originalHealth, player.getMaxHealth())));
              player.changeGameMode(oldMode);
              save("hostility", report);
            }
          });
    }
  }

  private static void rpg(ServerPlayerEntity player,Map<String,Object> report){
    var old=player.getMainHandStack().copy();
    var gear=new net.minecraft.item.ItemStack(net.minecraft.item.Items.IRON_SWORD);
    var tag=new net.minecraft.nbt.NbtCompound();tag.putBoolean("SVFrameItems",true);gear.set(net.minecraft.component.DataComponentTypes.CUSTOM_DATA,net.minecraft.component.type.NbtComponent.of(tag));player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,gear);
    var entity=spawn(player,"rattata level=30",5);entity.setAiDisabled(true);
    int hp=entity.getPokemon().getCurrentHealth(),max=entity.getPokemon().getMaxHealth();
    try{
      report.put("nativeHpBefore",hp);report.put("nativeMaxBefore",max);report.put("entityMaxBefore",entity.getMaxHealth());
      entity.damage(entity.getDamageSources().playerAttack(player),1);
      report.put("rpgActive",WildRpgHealth.active(entity));report.put("rpgMax",entity.getMaxHealth());report.put("rpgHealth",entity.getHealth());
      report.put("nativeHpAfterHit",entity.getPokemon().getCurrentHealth());report.put("nativeMaxAfterHit",entity.getPokemon().getMaxHealth());
      WildRpgHealth.suspend(entity);report.put("battleSuspended",!WildRpgHealth.active(entity));report.put("entityMaxSuspended",entity.getMaxHealth());
      report.put("nativeStatsUnchanged",hp==entity.getPokemon().getCurrentHealth()&&max==entity.getPokemon().getMaxHealth());
      SVFrameLibFabricMod.schedule(4,()->{report.put("restoredAfterBattle",WildRpgHealth.active(entity));report.put("restoredMax",entity.getMaxHealth());entity.discard();save("rpg",report);});
    }finally{player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,old);}
  }
  private static void temperament(ServerPlayerEntity player,Map<String,Object> report){
    player.changeGameMode(GameMode.SURVIVAL);player.clearStatusEffects();
    var previous=SVFrameMMOCobblemon.config().gameplay.temperament;SVFrameMMOCobblemon.config().gameplay.temperament=new TemperamentRules();
    report.put("policy","Fresh release defaults; existing admin overrides restored after probe");
    var friendly=spawn(player,"chansey level=30 moves=tackle",4);var neutral=spawn(player,"rattata level=30 moves=tackle",5);var hostile=spawn(player,"houndoom level=30 moves=tackle",6);var alpha=spawn(player,"rattata level=30 alpha=true moves=tackle",7);
    var entities=List.of(friendly,neutral,hostile,alpha);
    SVFrameLibFabricMod.schedule(30,()->{
      report.put("friendlyBefore",PokemonHostility.state(friendly));report.put("neutralBefore",PokemonHostility.state(neutral));report.put("hostileBefore",PokemonHostility.state(hostile));report.put("alphaBefore",PokemonHostility.state(alpha));
      friendly.damage(friendly.getDamageSources().playerAttack(player),1);neutral.damage(neutral.getDamageSources().playerAttack(player),1);
      SVFrameLibFabricMod.schedule(30,()->{report.put("friendlyProvoked",PokemonHostility.state(friendly));report.put("neutralProvoked",PokemonHostility.state(neutral));entities.forEach(Entity::discard);SVFrameMMOCobblemon.config().gameplay.temperament=previous;save("temperament",report);});
    });
  }

  private static void restoreLevel(ServerPlayerEntity player, Map<String, Object> report) {
    var data = SVFrameMMO.playerData().get(player);
    data.setLevel(
        ((Number) report.get("originalLevel")).intValue(), PlayerLevelChangeEvent.Reason.COMMAND);
    data.setExperience(((Number) report.get("originalExperience")).doubleValue());
  }

  private static void save(String mode, Map<String, Object> report) {
    try {
      Path path = Path.of("qa/cobblemon-gameplay-" + mode + ".json");
      Files.createDirectories(path.getParent());
      Files.writeString(
          path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
      SVFrameMMOCobblemon.LOG.info("Cobblemon gameplay QA {}: {}", mode, report);
    } catch (java.io.IOException error) {
      throw new IllegalStateException(error);
    }
  }
}
