package vn.svframe.svframemmo.cobblemon.gameplay;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.List;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.stat.provider.StatProvider;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.element.Element;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

/** Shared native move/damage adapter. Callers own wind-up, timing and encounter lifecycle. */
public final class PokemonOverworldCombat {
  private PokemonOverworldCombat() {}

  public static MoveTemplate choose(PokemonEntity entity) {
    for (var move : entity.getPokemon().getMoveSet())
      if (move.getTemplate().getPower() > 0) return move.getTemplate();
    return Moves.getByNameOrDummy("tackle");
  }

  public static boolean ranged(MoveTemplate move) {
    return "special".equalsIgnoreCase(move.getDamageCategory().getName());
  }

  public static void telegraph(MobEntity actor, ServerPlayerEntity target) {
    if (!(actor instanceof PokemonEntity entity)
        || !(actor.getWorld() instanceof ServerWorld world)) return;
    world.spawnParticles(
        ParticleTypes.ANGRY_VILLAGER,
        entity.getX(),
        entity.getBodyY(.8),
        entity.getZ(),
        5,
        .2,
        .15,
        .2,
        0);
    world.playSound(
        null,
        entity.getBlockPos(),
        SoundEvents.ENTITY_BLAZE_SHOOT,
        SoundCategory.HOSTILE,
        .35f,
        .7f);
    entity.getLookControl().lookAt(target, 30, 30);
    entity.playAnimation("battle_cry", List.of());
  }

  public static boolean strike(MobEntity actor, ServerPlayerEntity target) {
    return strike(actor, target, 1.0);
  }

  /**
   * Phase scaling is validated before resolving an actor; zero intentionally performs no attack.
   */
  public static boolean strike(MobEntity actor, ServerPlayerEntity target, double multiplier) {
    validateMultiplier(multiplier);
    return multiplier > 0
        && actor instanceof PokemonEntity entity
        && strike(entity, target, choose(entity), multiplier);
  }

  public static boolean strike(PokemonEntity entity, ServerPlayerEntity target, MoveTemplate move) {
    return strike(entity, target, move, 1.0);
  }

  private static void validateMultiplier(double multiplier) {
    if (!Double.isFinite(multiplier) || multiplier < 0 || multiplier > 20)
      throw new IllegalArgumentException(
          "Pokemon damage multiplier must be finite and within 0..20");
  }

  private static boolean strike(
      PokemonEntity entity, ServerPlayerEntity target, MoveTemplate move, double multiplier) {
    if (!entity.isAlive()
        || entity.isBattling()
        || entity.isBusy()
        || entity.getBeamMode() != 0
        || entity.getPokemon().isFainted()
        || target.getCommandTags().contains("svframe_cosmic_peace")
        || target.isCreative()
        || target.isSpectator()
        || !target.isAlive()
        || entity.getWorld() != target.getWorld()
        || !entity.getVisibilityCache().canSee(target)
        || !RpgEntityAdapters.allows(entity, target, RpgEntityAdapters.Effect.DAMAGE)) return false;
    double level = entity.getPokemon().getLevel();
    double damage =
        Math.min(
                SVFrameMMOCobblemon.config().gameplay.hostility.maximumDamage,
                2 + level * .04 + Math.max(0, move.getPower()) * .025)
            * multiplier;
    var type = move.getElementalType();
    var metadata =
        new AttackMetadata(
            new DamageMetadata(
                damage,
                Element.forDamage(type.getName(), type.getDisplayName().getString()),
                List.of(DamageType.SKILL, ranged(move) ? DamageType.MAGIC : DamageType.PHYSICAL)),
            target,
            StatProvider.get(entity));
    entity.playAnimation(ranged(move) ? "special" : "physical", List.of());
    SVFrameLib.plugin.getDamage().registerAttack(metadata, true, false);
    if (metadata.wasApplied() && entity.getWorld() instanceof ServerWorld world) {
      world.spawnParticles(
          ParticleTypes.CRIT,
          target.getX(),
          target.getBodyY(.5),
          target.getZ(),
          8,
          .25,
          .25,
          .25,
          .12);
      world.playSound(
          null,
          target.getBlockPos(),
          SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
          SoundCategory.HOSTILE,
          .7f,
          1);
    }
    return metadata.wasApplied();
  }

  public static boolean sweptHit(Vec3d from, Vec3d to, ServerPlayerEntity target) {
    return target.getBoundingBox().expand(.35).raycast(from, to).isPresent()
        || target.getBoundingBox().expand(.35).contains(from);
  }
}
