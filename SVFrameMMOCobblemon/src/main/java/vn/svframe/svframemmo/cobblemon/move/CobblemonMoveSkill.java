package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.entity.RpgEntityAdapters.Effect;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;
import vn.svframe.svframemmo.cobblemon.fusion.FusionSession;
import vn.svframe.svframemmo.cobblemon.integration.CobblemonEntityAdapter;
import vn.svframe.svframemmo.cobblemon.integration.CobblemonTypeChart;

public final class CobblemonMoveSkill extends SkillHandler<CobblemonMoveSkill.Result> {
   private static final double TARGET_RANGE = 18.0;
   private final String moveName;
   private final String moveId;
   private final MoveSemanticRegistry semantics;
   private final FusionService fusions;

   public CobblemonMoveSkill(ConfigObject config, String moveName, MoveSemanticRegistry semantics, FusionService fusions) {
      super(config);
      this.moveName = moveName;
      this.moveId = CobblemonMoveSkillAdapter.id(moveName);
      this.semantics = semantics;
      this.fusions = fusions;
   }

   public String moveId() {
      return this.moveId;
   }

   public String canonicalSkillId() {
      return CobblemonMoveSkillAdapter.canonicalId(this.moveId);
   }

   public MoveTemplate template() {
      return Moves.getByNameOrDummy(this.moveName);
   }

   public String getName() {
      return this.template().getDisplayName().getString();
   }

   public CobblemonMoveSkill.Result getResult(SkillMetadata metadata) {
      ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
      FusionSession activeFusion = this.fusions.session(player.getUuid());
      FusionService.MoveCast fusion = this.fusions.prepareMoveCast(player, this.moveId);
      if (activeFusion != null && activeFusion.activated() && fusion == null) {
         return CobblemonMoveSkill.Result.failed();
      } else {
         MoveTemplate move = fusion == null ? this.template() : fusion.move().getTemplate();
         CobblemonMoveProfile profile = CobblemonMoveProfile.of(move);
         MoveSemantic semantic = this.semantics.resolve(move);
         boolean requiresTarget = profile.requiresSingleTarget()
            || profile.executor() != CobblemonMoveProfile.Executor.AOE && semanticNeedsTarget(move, semantic);
         LivingEntity target = !requiresTarget && profile.executor() != CobblemonMoveProfile.Executor.DASH
            ? null
            : acquireTarget(
               player, fusion == null ? null : fusion.session().deployedEntityUuid(), profile.range(), move.getPower() > 0.0 ? Effect.DAMAGE : Effect.STATUS
            );
         if (requiresTarget && target == null) {
            return CobblemonMoveSkill.Result.failed();
         } else {
            Vec3d teleport = profile.executor() == CobblemonMoveProfile.Executor.TELEPORT ? teleportDestination(player, 12.0) : null;
            return profile.executor() == CobblemonMoveProfile.Executor.TELEPORT && teleport == null
               ? CobblemonMoveSkill.Result.failed()
               : new CobblemonMoveSkill.Result(true, fusion, move, profile, semantic, target, teleport);
         }
      }
   }

   public void whenCast(CobblemonMoveSkill.Result result, SkillMetadata metadata) {
      if (result.success) {
         ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
         SkillMetadata elementalMetadata = new CobblemonElementalSkillMetadata(metadata, result.move.getElementalType());
         if (result.fusion != null) {
            if (!this.fusions.consumePp(result.fusion)) {
               return;
            }

            this.fusions.executeMove(result.fusion, result.target, result.semantic, elementalMetadata);
         } else {
            this.executeStandalone(player, result, elementalMetadata);
         }

         if (result.fusion != null && result.target != null) {
            SVFrameMMOCobblemon.moveVfx().renderImpact(player, result.move, result.target);
         }
      }
   }

   private void executeStandalone(ServerPlayerEntity player, CobblemonMoveSkill.Result result, SkillMetadata metadata) {
      MoveTemplate move = result.move;
      CobblemonMoveProfile profile = result.profile;
      MoveSemantic semantic = result.semantic;
      switch (profile.executor()) {
         case HEAL:
            double healing = OverworldMoveMath.healing(player.getMaxHealth(), semantic.healFraction(), metadata.getParameter("healing"), profile.healBase());
            player.heal((float)healing);
            if (profile.cleanse()) {
               cleanse(player);
            }
            break;
         case SHIELD:
            this.fusions.grantProtection(player, 20L);
            break;
         case WEATHER:
            applyWeather(player.getServerWorld(), profile.weather());
            break;
         case TELEPORT:
            player.requestTeleport(result.teleport.x, result.teleport.y, result.teleport.z);
            break;
         case DASH:
            Vec3d look = player.getRotationVec(1.0F).normalize();
            Vec3d velocity = player.getVelocity().multiply(0.35).add(look.multiply(profile.dashStrength())).add(0.0, 0.12, 0.0);
            player.setVelocity(velocity);
            player.velocityModified = true;
            this.executeDamage(player, result.target, move, profile, semantic, metadata);
            break;
         case AOE:
            this.executeAoe(player, move, profile, semantic, metadata);
            break;
         case SELF_BUFF:
            this.applySelfSemantic(player, semantic, 0.0);
            break;
         case TARGET_DEBUFF:
            if (result.target != null
               && RpgEntityAdapters.allows(player, result.target, Effect.STATUS)
               && this.fusions.rollMoveAccuracy(player, result.target, move)) {
               this.applyTargetSemantic(player, result.target, semantic);
               SVFrameMMOCobblemon.moveVfx().renderImpact(player, move, result.target);
            }
            break;
         case TARGET:
         case PROJECTILE:
            this.executeDamage(player, result.target, move, profile, semantic, metadata);
      }
   }

   private void executeAoe(ServerPlayerEntity player, MoveTemplate move, CobblemonMoveProfile profile, MoveSemantic semantic, SkillMetadata metadata) {
      Box box = player.getBoundingBox().expand(profile.radius());

      for (LivingEntity target : player.getServerWorld()
         .getEntitiesByClass(
            LivingEntity.class,
            box,
            living -> living.isAlive()
               && living != player
               && living.squaredDistanceTo(player) <= profile.radius() * profile.radius()
               && player.canSee(living)
               && RpgEntityAdapters.allows(player, living, move.getPower() > 0.0 ? Effect.DAMAGE : Effect.STATUS)
               && !this.fusions.isVisualEntityOf(player.getUuid(), living.getUuid())
         )) {
         if (move.getPower() > 0.0) {
            this.executeDamage(player, target, move, profile, semantic, metadata);
         } else if (this.fusions.rollMoveAccuracy(player, target, move)) {
            this.applyTargetSemantic(player, target, semantic);
            SVFrameMMOCobblemon.moveVfx().renderImpact(player, move, target);
         }
      }
   }

   private void executeDamage(
      ServerPlayerEntity player, LivingEntity target, MoveTemplate move, CobblemonMoveProfile profile, MoveSemantic semantic, SkillMetadata metadata
   ) {
      if (target != null && RpgEntityAdapters.allows(player, target, Effect.DAMAGE) && this.fusions.rollMoveAccuracy(player, target, move)) {
         int hits = semantic.multiHitMin() == semantic.multiHitMax()
            ? semantic.multiHitMin()
            : ThreadLocalRandom.current().nextInt(semantic.multiHitMin(), semantic.multiHitMax() + 1);
         double configuredDamage = Math.max(0.0, metadata.getParameter("damage"));
         double perHit = Math.max(1.0, configuredDamage > 0.0 ? configuredDamage : profile.baseDamage());
         double dealt = perHit * Math.max(1, hits) * this.fusions.moveDamageMultiplier(player, target, move);
         if (target instanceof PokemonEntity pokemon) {
            if (!CobblemonTypeChart.ready()) {
               return;
            }

            double effectiveness = CobblemonTypeChart.effectiveness(move.getElementalType().getName(), pokemon.getPokemon().getForm().getTypes());
            if (effectiveness <= 0.0) {
               return;
            }

            dealt *= effectiveness;
         }

         DamageType category = profile.damageCategory().equals("physical") ? DamageType.PHYSICAL : DamageType.MAGIC;
         AttackMetadata attack = metadata.attack(target, dealt, new DamageType[]{DamageType.SKILL, category});
         if (attack.wasApplied()) {
            this.applyTargetSemantic(player, target, semantic);
            this.applySelfSemantic(player, semantic, attack.getAppliedDamage());
            SVFrameMMOCobblemon.moveVfx().renderImpact(player, move, target);
         }
      }
   }

   private void applySelfSemantic(ServerPlayerEntity player, MoveSemantic semantic, double dealt) {
      for (MoveSemantic.StageChange change : semantic.stages()) {
         if (change.target() == MoveSemantic.Target.SELF && ThreadLocalRandom.current().nextDouble() < change.chance()) {
            this.fusions.applyMoveStage(player, change.stat(), change.stages());
         }
      }

      if (semantic.healFraction() > 0.0) {
         player.heal((float)(player.getMaxHealth() * semantic.healFraction()));
      }

      if (dealt > 0.0 && semantic.drainFraction() > 0.0) {
         player.heal((float)(dealt * semantic.drainFraction()));
      }

      if (dealt > 0.0 && semantic.recoilFraction() > 0.0) {
         player.damage(player.getDamageSources().generic(), (float)(dealt * semantic.recoilFraction()));
      }

      if (semantic.protect()) {
         this.fusions.grantProtection(player, 20L);
      }
   }

   private void applyTargetSemantic(ServerPlayerEntity actor, LivingEntity target, MoveSemantic semantic) {
      if (RpgEntityAdapters.allows(actor, target, Effect.STATUS)) {
         for (MoveSemantic.StageChange change : semantic.stages()) {
            if (change.target() == MoveSemantic.Target.TARGET && ThreadLocalRandom.current().nextDouble() < change.chance()) {
               this.fusions.applyMoveStage(target, change.stat(), change.stages());
            }
         }

         if (semantic.status() != MoveSemantic.Status.NONE && ThreadLocalRandom.current().nextDouble() < semantic.statusChance()) {
            applyStatus(actor, target, semantic.status());
         }
      }
   }

   private static void applyStatus(ServerPlayerEntity actor, LivingEntity target, MoveSemantic.Status status) {
      if (target instanceof PokemonEntity pokemon) {
         String id = switch (status) {
            case PARALYSIS -> "paralysis";
            case BURN -> "burn";
            case POISON -> "poison";
            case BAD_POISON -> "poisonbadly";
            case SLEEP -> "sleep";
            case FREEZE -> "frozen";
            default -> "";
         };
         if (!id.isEmpty()) {
            CobblemonEntityAdapter.INSTANCE.status(actor, pokemon, id);
            return;
         }
      }

      switch (status) {
         case PARALYSIS:
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
            break;
         case BURN:
            target.setOnFireFor(4.0F);
            break;
         case POISON:
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 0));
            break;
         case BAD_POISON:
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 1));
            break;
         case SLEEP:
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 5));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
            break;
         case FREEZE:
            target.setFrozenTicks(Math.max(target.getFrozenTicks(), 160));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
            break;
         case CONFUSION:
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
            break;
         case FLINCH:
            target.setVelocity(Vec3d.ZERO);
            target.velocityModified = true;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 5));
         case NONE:
      }
   }

   private static void cleanse(ServerPlayerEntity player) {
      for (StatusEffectInstance instance : List.copyOf(player.getStatusEffects())) {
         if (!((StatusEffect)instance.getEffectType().value()).isBeneficial()) {
            player.removeStatusEffect(instance.getEffectType());
         }
      }
   }

   private static void applyWeather(ServerWorld world, String weather) {
      if ("rain".equals(weather)) {
         world.setWeather(0, 400, true, false);
      } else {
         world.setWeather(400, 0, false, false);
      }
   }

   private static Vec3d teleportDestination(ServerPlayerEntity player, double maxDistance) {
      Vec3d start = player.getPos();
      Vec3d look = player.getRotationVec(1.0F).normalize();
      ServerWorld world = player.getServerWorld();

      for (int step = (int)Math.floor(maxDistance); step >= 1; step--) {
         Vec3d candidate = start.add(look.multiply(step));
         BlockPos feet = BlockPos.ofFloored(candidate);
         BlockPos head = feet.up();
         BlockPos floor = feet.down();
         if (world.getWorldBorder().contains(feet)
            && world.getBlockState(feet).isAir()
            && world.getBlockState(head).isAir()
            && !world.getBlockState(floor).getCollisionShape(world, floor).isEmpty()
            && world.isSpaceEmpty(player, player.getBoundingBox().offset(candidate.subtract(start)))) {
            return candidate;
         }
      }

      return null;
   }

   private static boolean semanticNeedsTarget(MoveTemplate move, MoveSemantic semantic) {
      return move.getPower() > 0.0
         ? false
         : semantic.status() != MoveSemantic.Status.NONE || semantic.stages().stream().anyMatch(change -> change.target() == MoveSemantic.Target.TARGET);
   }

   private static LivingEntity acquireTarget(ServerPlayerEntity player, UUID excludedEntity, double range, Effect effect) {
      double actualRange = range <= 0.0 ? 18.0 : range;
      Vec3d eye = player.getEyePos();
      Vec3d look = player.getRotationVec(1.0F).normalize();
      Box search = player.getBoundingBox().stretch(look.multiply(actualRange)).expand(2.0);
      List<Entity> candidates = player.getWorld()
         .getOtherEntities(
            player,
            search,
            entity -> entity instanceof LivingEntity living && living.isAlive() && (excludedEntity == null || !entity.getUuid().equals(excludedEntity))
         );
      return candidates.stream()
         .map(entity -> (LivingEntity)entity)
         .filter(
            entity -> {
               Vec3d delta = entity.getBoundingBox().getCenter().subtract(eye);
               return delta.lengthSquared() > 1.0E-4
                  && delta.lengthSquared() <= actualRange * actualRange
                  && player.canSee(entity)
                  && RpgEntityAdapters.allows(player, entity, effect)
                  && delta.normalize().dotProduct(look) >= 0.92;
            }
         )
         .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
         .orElse(null);
   }

   public static final class Result implements SkillResult {
      private final boolean success;
      private final FusionService.MoveCast fusion;
      private final MoveTemplate move;
      private final CobblemonMoveProfile profile;
      private final MoveSemantic semantic;
      private final LivingEntity target;
      private final Vec3d teleport;

      private Result(
         boolean success,
         FusionService.MoveCast fusion,
         MoveTemplate move,
         CobblemonMoveProfile profile,
         MoveSemantic semantic,
         LivingEntity target,
         Vec3d teleport
      ) {
         this.success = success;
         this.fusion = fusion;
         this.move = move;
         this.profile = profile;
         this.semantic = semantic;
         this.target = target;
         this.teleport = teleport;
      }

      static CobblemonMoveSkill.Result failed() {
         return new CobblemonMoveSkill.Result(false, null, null, null, null, null, null);
      }

      public boolean isSuccessful() {
         return this.success;
      }
   }
}
