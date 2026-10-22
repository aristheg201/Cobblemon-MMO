package vn.svframe.svframemmo.cobblemon.gameplay;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/**
 * One combat owner per wild Pokémon. The entity's native movement controller still performs
 * pathing.
 */
public final class PokemonHostility {
  private static final Map<PokemonEntity, State> STATES = new WeakHashMap<>();
  private static final PursuitSlots SLOTS = new PursuitSlots();

  private static final class State {
    UUID target;
    Vec3d home, bolt, direction;
    MoveTemplate move;
    long nextAttack, ready, pauseUntil, reacquire;
    int unseen, travel;
  }

  private PokemonHostility() {}

  public static void register() {
    CobblemonEvents.LOOT_DROPPED.subscribe(
        event -> {
          if (event.getEntity().getCommandTags().contains("svframe_worldboss")) event.cancel();
        });
    CobblemonEvents.POKEMON_RECALL_PRE.subscribe(
        event -> {
          if (event.getOldEntity() != null
              && event.getOldEntity().getCommandTags().contains("svframe_worldboss"))
            event.cancel();
        });
    CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(
        event -> {
          var pokemon = event.getPokemon();
          if (pokemon.getCommandTags().contains("svframe_worldboss")) {
            event.cancel();
            return;
          }
          if (claims(pokemon)) {
            var state = STATES.computeIfAbsent(pokemon, p -> new State());
            release(pokemon, state);
            state.pauseUntil = pokemon.getWorld().getTime() + 100;
          }
        });
    CobblemonEvents.BATTLE_STARTED_PRE.subscribe(
        event -> {
          for (var actor : event.getBattle().getActors())
            for (var battler : actor.getPokemonList()) {
              var pokemon = battler.getOriginalPokemon().getEntity();
              if (pokemon != null && pokemon.getCommandTags().contains("svframe_worldboss")) {
                event.cancel();
                return;
              }
            }
        });
    CobblemonEvents.BATTLE_STARTED_POST.subscribe(
        event -> {
          for (var actor : event.getBattle().getActors())
            for (var battler : actor.getPokemonList()) {
              var pokemon = battler.getOriginalPokemon().getEntity();
              if (pokemon != null) {
                var state = STATES.get(pokemon);
                if (state != null) release(pokemon, state);
              }
            }
        });
    ServerEntityEvents.ENTITY_UNLOAD.register(
        (entity, world) -> {
          if (entity instanceof PokemonEntity pokemon) {
            STATES.remove(pokemon);
            SLOTS.release(pokemon.getUuid());
            // OmniPathNavigation.stop queries blocks. During chunk unload that can
            // reload the same chunk recursively and register its entities twice.
            // The unloaded actor needs only bookkeeping cleanup, not navigation.
          }
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        server -> {
          STATES.clear();
          SLOTS.clear();
        });
  }

  /**
   * This remains true while busy/battling/capturing, preventing FoF from taking over during
   * protection.
   */
  public static boolean claims(PokemonEntity entity) {
    if (entity.getWorld().isClient()) return false;
    var h = SVFrameMMOCobblemon.config().gameplay.hostility;
    var pokemon = entity.getPokemon();
    String dimension = entity.getWorld().getRegistryKey().getValue().toString();
    boolean excluded =
        h.excludedDimensions.contains(dimension)
            || h.excludedSpecies.contains(pokemon.getSpecies().getResourceIdentifier().toString())
            || h.excludedSpecies.contains(pokemon.getSpecies().getName().toLowerCase(Locale.ROOT))
            || entity.getCommandTags().contains("svframe_qa")
            || entity.getCommandTags().contains("svframe_no_hostility")
            || entity.isAiDisabled();
    for (var region : h.excludedRegions)
      if (region.contains(dimension, entity.getX(), entity.getY(), entity.getZ())) {
        excluded = true;
        break;
      }
    return HostilityRules.claims(
        h.enabled,
        pokemon.getOwnerUUID() != null,
        pokemon.isBattleClone(),
        entity.getCommandTags().contains("svframe_worldboss"),
        excluded);
  }

  public static boolean validPlayer(ServerPlayerEntity player) {
    return player != null
        && player.isAlive()
        && !player.isCreative()
        && !player.isSpectator()
        && !player.getCommandTags().contains("svframe_cosmic_peace")
        && BattleRegistry.getBattleByParticipatingPlayer(player) == null;
  }

  public static boolean safe(PokemonEntity pokemon, ServerPlayerEntity player) {
    return HostilityRules.canAttack(
        pokemon.isAlive(),
        pokemon.isBattling(),
        pokemon.isBusy(),
        pokemon.getPokemon().isFainted(),
        pokemon.getBeamMode() != 0,
        !validPlayer(player));
  }

  /** Called at mobTick HEAD. true means the ordinary/FoF brain must not run this tick. */
  public static boolean tick(PokemonEntity pokemon) {
    State state = STATES.get(pokemon);
    if (!claims(pokemon)) {
      if (state != null) {
        release(pokemon, state);
        STATES.remove(pokemon);
      }
      // Scheduled bosses own their brain while native navigation and physics keep running.
      return pokemon.getCommandTags().contains("svframe_worldboss");
    }
    if (state == null) {
      state = new State();
      STATES.put(pokemon, state);
    }
    var world = (ServerWorld) pokemon.getWorld();
    long now = world.getTime();
    var h = SVFrameMMOCobblemon.config().gameplay.hostility;
    // Clear the competing brain's targets even while ambient movement remains enabled.
    pokemon.setTarget(null);
    pokemon.getBrain().forget(MemoryModuleType.ATTACK_TARGET);
    pokemon.getBrain().forget(MemoryModuleType.ANGRY_AT);
    if (now < state.pauseUntil
        || !pokemon.isAlive()
        || pokemon.isBattling()
        || pokemon.isBusy()
        || pokemon.getPokemon().isFainted()
        || pokemon.getBeamMode() != 0) {
      release(pokemon, state);
      return true;
    }
    ServerPlayerEntity player =
        state.target == null ? null : world.getServer().getPlayerManager().getPlayer(state.target);
    if (state.target != null
        && (!safe(pokemon, player)
            || player.getWorld() != world
            || pokemon.squaredDistanceTo(state.home) > h.leashRange * h.leashRange
            || pokemon.squaredDistanceTo(player) > h.leashRange * h.leashRange)) {
      release(pokemon, state);
      state.reacquire = now + 40;
      player = null;
    }
    if (player == null
        && now >= state.reacquire
        && now % 10 == Math.floorMod(pokemon.getId(), 10)) {
      var candidates =
          world.getPlayers(
              p ->
                  validPlayer(p)
                      && pokemon.squaredDistanceTo(p) <= h.detectRange * h.detectRange
                      && pokemon.getVisibilityCache().canSee(p));
      candidates.sort(Comparator.comparingDouble(pokemon::squaredDistanceTo));
      for (var candidate : candidates)
        if (SLOTS.claim(pokemon.getUuid(), candidate.getUuid(), h.maxAttackersPerPlayer)) {
          state.target = candidate.getUuid();
          state.home = pokemon.getPos();
          state.nextAttack = now + 5;
          player = candidate;
          break;
        }
    }
    if (player == null) return false;
    if (!pokemon.getVisibilityCache().canSee(player)) {
      if (++state.unseen >= h.loseTargetTicks) {
        release(pokemon, state);
        state.reacquire = now + 40;
        return true;
      }
    } else state.unseen = 0;
    pokemon.getLookControl().lookAt(player, 30, 30);
    if (state.bolt != null) {
      tickBolt(pokemon, player, state);
      return true;
    }
    if (state.ready > 0) {
      pokemon.getNavigation().stop();
      if (now % 4 == 0)
        world.spawnParticles(
            ParticleTypes.ENCHANT,
            pokemon.getX(),
            pokemon.getBodyY(.7),
            pokemon.getZ(),
            3,
            .25,
            .2,
            .25,
            .05);
      if (now >= state.ready) {
        state.ready = 0;
        state.nextAttack = now + h.cooldownTicks;
        if (PokemonOverworldCombat.ranged(state.move)) {
          state.bolt = pokemon.getPos().add(0, pokemon.getHeight() * .65, 0);
          state.travel = 0;
          // Snapshot the direction once. Players can dodge the actual moving bolt.
          state.direction =
              player
                  .getPos()
                  .add(0, player.getHeight() * .5, 0)
                  .subtract(state.bolt)
                  .normalize()
                  .multiply(.65);
        } else if (pokemon.squaredDistanceTo(player) <= meleeRange(pokemon) * meleeRange(pokemon))
          PokemonOverworldCombat.strike(pokemon, player, state.move);
      }
      return true;
    }
    MoveTemplate move = PokemonOverworldCombat.choose(pokemon);
    double range = PokemonOverworldCombat.ranged(move) ? 9 : meleeRange(pokemon);
    if (pokemon.squaredDistanceTo(player) > range * range
        || !pokemon.getVisibilityCache().canSee(player)) {
      if (now % 8 == 0) pokemon.getNavigation().startMovingTo(player, h.movementSpeed);
    } else if (now >= state.nextAttack) {
      pokemon.getNavigation().stop();
      state.move = move;
      state.ready = now + h.windupTicks;
      PokemonOverworldCombat.telegraph(pokemon, player);
    } else pokemon.getNavigation().stop();
    return true;
  }

  private static double meleeRange(PokemonEntity pokemon) {
    return Math.min(3, 1.6 + pokemon.getWidth() * .5);
  }

  private static void tickBolt(PokemonEntity pokemon, ServerPlayerEntity player, State state) {
    var world = (ServerWorld) pokemon.getWorld();
    Vec3d next = state.bolt.add(state.direction);
    var block =
        world.raycast(
            new RaycastContext(
                state.bolt,
                next,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                pokemon));
    if (block.getType() != HitResult.Type.MISS || ++state.travel > 24) {
      state.bolt = null;
      return;
    }
    world.spawnParticles(
        ParticleTypes.ELECTRIC_SPARK, next.x, next.y, next.z, 4, .08, .08, .08, .02);
    if (PokemonOverworldCombat.sweptHit(state.bolt, next, player)) {
      PokemonOverworldCombat.strike(pokemon, player, state.move);
      state.bolt = null;
    } else state.bolt = next;
  }

  private static void release(PokemonEntity pokemon, State state) {
    SLOTS.release(pokemon.getUuid());
    state.target = null;
    state.home = null;
    state.bolt = null;
    state.direction = null;
    state.ready = 0;
    state.unseen = 0;
    pokemon.getNavigation().stop();
    pokemon.setTarget(null);
  }

  public static String state(PokemonEntity pokemon) {
    var state = STATES.get(pokemon);
    return state == null
        ? "idle"
        : state.target
            + ":"
            + (state.bolt != null ? "projectile" : state.ready > 0 ? "windup" : "pursuit");
  }
}
