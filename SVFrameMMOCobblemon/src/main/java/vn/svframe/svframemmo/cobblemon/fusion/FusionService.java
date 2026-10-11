package vn.svframe.svframemmo.cobblemon.fusion;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.move.BattleStat;
import vn.svframe.svframemmo.cobblemon.move.CobblemonMoveSkillAdapter;
import vn.svframe.svframemmo.cobblemon.move.MoveSemantic;
import vn.svframe.svframemmo.cobblemon.move.MoveSemanticRegistry;
import vn.svframe.svframemmo.cobblemon.move.RealtimeBattleState;
import vn.svframe.svframemmo.skill.runtime.TemporarySkillOverlayRuntime.Handle;

public final class FusionService {
   public static final long DEFAULT_DANCE_DURATION_TICKS = 12000L;
   private static final long STAGE_DURATION_TICKS = 600L;
   private static final long PROTECT_DURATION_TICKS = 20L;
   private static final String OVERLAY_OWNER = "svframemmo_cobblemon:fusion";
   private final DeployedPartyPokemonResolver resolver = new DeployedPartyPokemonResolver();
   private final FusionEligibility eligibility = new FusionEligibility();
   private final MoveSemanticRegistry semantics = new MoveSemanticRegistry();
   private final RealtimeBattleState realtime = new RealtimeBattleState();
   private final Map<UUID, LivingEntity> moveSpeedSubjects = new ConcurrentHashMap<>();
   private static final Identifier MOVE_SPEED_MODIFIER = Identifier.of("svframemmo_cobblemon", "move_speed_stage");
   private final CobblemonMoveSkillAdapter moves = new CobblemonMoveSkillAdapter(this, this.semantics);
   private final FusionCooldowns cooldowns = new FusionCooldowns();
   private final FusionVisualBridge visuals = new FusionVisualBridge();
   private final FusionStatBridge stats = new FusionStatBridge();
   private final Map<UUID, FusionSession> byPlayer = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> lockedPokemon = new ConcurrentHashMap<>();
   private final ThreadLocal<UUID> executingMoveDamage = new ThreadLocal<>();

   public FusionSession session(UUID player) {
      return player == null ? null : this.byPlayer.get(player);
   }

   public boolean isPokemonLocked(UUID pokemon) {
      return pokemon != null && this.lockedPokemon.containsKey(pokemon);
   }

   public int activeCount() {
      return this.byPlayer.size();
   }

   public FusionCooldowns cooldowns() {
      return this.cooldowns;
   }

   public int potaraCooldownSeconds() {
      return SVFrameMMOCobblemon.config().fusion.potaraActionCooldownSeconds;
   }

   public FusionService.StartResult startPotara(ServerPlayerEntity player, PokemonEntity entity, FusionTier tier) {
      if (tier != null && tier != FusionTier.DANCE) {
         DeployedPartyPokemonResolver.Resolution resolved = this.resolver.resolve(player, entity);
         return !resolved.accepted() ? FusionService.StartResult.rejected(resolved.rejection()) : this.startResolved(player, resolved.pokemon(), tier, -1L);
      } else {
         return FusionService.StartResult.rejected("Invalid Potara tier.");
      }
   }

   public FusionService.StartResult startDance(ServerPlayerEntity player, Pokemon selected) {
      if (!this.cooldowns.danceReady(player.getUuid())) {
         return FusionService.StartResult.rejected("Fusion Dance is still on cooldown.");
      } else if (selected == null) {
         return FusionService.StartResult.rejected("No Pokemon was selected.");
      } else {
         Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(selected.getUuid());
         if (pokemon != null && pokemon.belongsTo(player)) {
            PokemonEntity deployed = pokemon.getEntity();
            if (deployed != null && deployed.isBattling()) {
               return FusionService.StartResult.rejected("That Pokemon is currently battling.");
            } else {
               long expiresAt = SVFrameMMO.currentTick() + SVFrameMMOCobblemon.config().fusion.danceDurationSeconds * 20L;
               return this.startResolved(player, pokemon, FusionTier.DANCE, expiresAt);
            }
         } else {
            return FusionService.StartResult.rejected("That Pokemon is not in your current party.");
         }
      }
   }

   private FusionService.StartResult startResolved(ServerPlayerEntity player, Pokemon pokemon, FusionTier tier, long expiresAt) {
      UUID playerId = player.getUuid();
      UUID pokemonId = pokemon.getUuid();
      if (this.byPlayer.containsKey(playerId)) {
         return FusionService.StartResult.rejected("You are already fused.");
      } else if (!this.eligibility.allows(tier, pokemon)) {
         return FusionService.StartResult.rejected("This Pokemon is not eligible for that fusion rank.");
      } else if (this.lockedPokemon.containsKey(pokemonId)) {
         return FusionService.StartResult.rejected("That Pokemon is already locked by a fusion.");
      } else {
         PokemonEntity deployed = pokemon.getEntity();
         if (tier == FusionTier.DANCE || deployed != null && !deployed.isRemoved()) {
            if (deployed != null && deployed.isBattling()) {
               return FusionService.StartResult.rejected("That Pokemon is currently battling.");
            } else {
               boolean originalTradeable = pokemon.getTradeable();
               Handle overlay = null;

               try {
                  CobblemonMoveSkillAdapter.Overlay snapshot = this.moves.snapshot(pokemon);
                  recallForFusionOrThrow(pokemon);
                  if (this.lockedPokemon.putIfAbsent(pokemonId, playerId) != null) {
                     throw new IllegalStateException("That Pokemon became locked by another fusion.");
                  } else {
                     pokemon.setTradeable(false);
                     overlay = SVFrameMMO.temporarySkills().push(playerId, "svframemmo_cobblemon:fusion", snapshot.skills());
                     FusionSession session = new FusionSession(
                        playerId,
                        pokemonId,
                        null,
                        pokemon.getSpecies().getResourceIdentifier().toString(),
                        pokemon.getDisplayName(false).getString(),
                        tier,
                        SVFrameMMO.currentTick(),
                        expiresAt,
                        tier != FusionTier.DANCE,
                        originalTradeable,
                        false,
                        snapshot.moveIds(),
                        overlay
                     );
                     this.stats.apply(player, pokemon, tier);
                     refillFusionResources(player);
                     if (this.byPlayer.putIfAbsent(playerId, session) != null) {
                        throw new IllegalStateException("Fusion session already exists");
                     } else {
                        this.visuals.start(player, pokemon);
                        return new FusionService.StartResult(session, null);
                     }
                  }
               } catch (RuntimeException var13) {
                  if (overlay != null) {
                     overlay.close();
                  }

                  this.stats.remove(player);
                  refillFusionResources(player);
                  this.byPlayer.remove(playerId);
                  this.visuals.stop(player, playerId);
                  pokemon.setTradeable(originalTradeable);
                  this.lockedPokemon.remove(pokemonId, playerId);
                  SVFrameMMOCobblemon.LOG
                     .warn(
                        "Could not start {} fusion for {}",
                        new Object[]{tier == FusionTier.DANCE ? "Dance" : "Potara", player.getName().getString(), var13}
                     );
                  return FusionService.StartResult.rejected("Could not start fusion: " + safeMessage(var13));
               }
            }
         } else {
            return FusionService.StartResult.rejected("Potara requires the selected Pokemon to be deployed.");
         }
      }
   }

   private static void recallForFusionOrThrow(Pokemon pokemon) {
      PokemonEntity deployed = pokemon.getEntity();
      if (deployed != null && !deployed.isRemoved()) {
         if (deployed.isBattling()) {
            throw new IllegalStateException("That Pokemon is currently battling.");
         } else {
            pokemon.recall();
            PokemonEntity remaining = pokemon.getEntity();
            if (remaining != null && !remaining.isRemoved()) {
               throw new IllegalStateException("Pokemon recall was cancelled; fusion will not create a duplicate Pokemon entity.");
            }
         }
      }
   }

   public FusionService.EndResult end(ServerPlayerEntity player, boolean manual) {
      FusionSession session = this.byPlayer.get(player.getUuid());
      if (session == null) {
         return FusionService.EndResult.rejected("You are not fused.");
      } else {
         return manual && !session.manualUnfuseAllowed()
            ? FusionService.EndResult.rejected("Fusion Dance cannot be manually unfused.")
            : this.finish(player, session);
      }
   }

   private FusionService.EndResult finish(ServerPlayerEntity player, FusionSession session) {
      if (!this.byPlayer.remove(session.playerUuid(), session)) {
         return FusionService.EndResult.rejected("Fusion session already ended.");
      } else {
         this.lockedPokemon.remove(session.pokemonUuid(), session.playerUuid());
         if (session.overlay() != null) {
            session.overlay().close();
         }

         this.clearMoveState(session.playerUuid());
         if (player != null) {
            this.stats.remove(player);
            refillFusionResources(player);
         }

         Pokemon pokemon = player == null ? null : Cobblemon.INSTANCE.getStorage().getParty(player).get(session.pokemonUuid());
         if (pokemon != null) {
            pokemon.setTradeable(session.originalTradeable());
         }

         this.visuals.stop(player, session.playerUuid());
         if (session.dance()) {
            this.cooldowns.markDance(session.playerUuid(), SVFrameMMOCobblemon.config().fusion.danceCooldownSeconds);
         }

         return new FusionService.EndResult(session, null);
      }
   }

   private static void refillFusionResources(ServerPlayerEntity player) {
      if (player != null) {
         PlayerData data = SVFrameMMO.playerData().get(player);

         for (PlayerResource resource : PlayerResource.values()) {
            data.setResource(resource, data.getMaxResource(resource), ResourceUpdateReason.CHOOSE_CLASS);
         }
      }
   }

   public void onDisconnect(ServerPlayerEntity player) {
      FusionSession session = this.byPlayer.get(player.getUuid());
      if (session != null) {
         this.finish(player, session);
      }

      this.clearMoveState(player.getUuid());
   }

   public void tick(long tick, MinecraftServer server) {
      this.realtime.tick(tick);

      for (LivingEntity subject : List.copyOf(this.moveSpeedSubjects.values())) {
         if (!subject.isAlive() || subject.isRemoved() || this.realtime.stage(subject.getUuid(), BattleStat.SPEED, tick) == 0) {
            this.removeMoveSpeed(subject.getUuid());
         }
      }

      if (!this.byPlayer.isEmpty()) {
         for (UUID invalid : this.visuals.tick(server)) {
            FusionSession session = this.byPlayer.get(invalid);
            if (session != null) {
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(invalid);
               if (player != null) {
                  this.finish(player, session);
               }
            }
         }

         for (FusionSession session : this.byPlayer.values()) {
            if (session.expired(tick)) {
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(session.playerUuid());
               if (player != null) {
                  this.finish(player, session);
               }
            }
         }
      }
   }

   public FusionService.MoveCast prepareMoveCast(ServerPlayerEntity player, String moveId) {
      FusionSession session = this.byPlayer.get(player.getUuid());
      if (session != null && session.activated() && session.moveIds().contains(moveId)) {
         Pokemon pokemon = Cobblemon.INSTANCE.getStorage().getParty(player).get(session.pokemonUuid());
         if (pokemon == null) {
            return null;
         } else if (pokemon.getEntity() != null && pokemon.getEntity().isBattling()) {
            return null;
         } else {
            for (Move move : pokemon.getMoveSet().getMoves()) {
               if (move != null && CobblemonMoveSkillAdapter.id(move.getName()).equals(moveId)) {
                  if (move.getCurrentPp() <= 0) {
                     player.sendMessage(Text.literal(move.getDisplayName().getString() + " has no PP left."), true);
                     return null;
                  }

                  return new FusionService.MoveCast(session, pokemon, move);
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   public boolean consumePp(FusionService.MoveCast cast) {
      Move move = cast.move();
      if (move.getCurrentPp() <= 0) {
         return false;
      } else {
         move.setCurrentPp(move.getCurrentPp() - 1);
         move.update();
         return true;
      }
   }

   public void executeMove(FusionService.MoveCast cast, LivingEntity target, MoveSemantic semantic, SkillMetadata metadata) {
      MoveTemplate move = cast.move().getTemplate();
      ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
      long tick = SVFrameMMO.currentTick();
      if (semantic.protect()) {
         this.realtime.protect(player.getUuid(), tick, 20L);
      }

      for (MoveSemantic.StageChange change : semantic.stages()) {
         UUID subject = change.target() == MoveSemantic.Target.SELF ? player.getUuid() : (target == null ? null : target.getUuid());
         if (subject != null && ThreadLocalRandom.current().nextDouble() < change.chance()) {
            this.applyMoveStage((LivingEntity)(change.target() == MoveSemantic.Target.SELF ? player : target), change.stat(), change.stages());
         }
      }

      boolean hit = target == null || this.rollAccuracy(player, target, move, tick);
      double dealt = 0.0;
      if (hit && target != null && move.getPower() > 0.0) {
         int hits = semantic.multiHitMin() == semantic.multiHitMax()
            ? semantic.multiHitMin()
            : ThreadLocalRandom.current().nextInt(semantic.multiHitMin(), semantic.multiHitMax() + 1);
         double perHit = rawMoveDamage(metadata, cast, move) * this.moveDamageMultiplier(player, target, move);
         dealt = perHit * Math.max(1, hits);
         this.applyMoveAttack(player, metadata, target, dealt, move);
      }

      if (hit && semantic.status() != MoveSemantic.Status.NONE && target != null && ThreadLocalRandom.current().nextDouble() < semantic.statusChance()) {
         applyStatus(target, semantic.status());
      }

      if (semantic.healFraction() > 0.0) {
         player.heal((float)(player.getMaxHealth() * semantic.healFraction()));
      }

      if (dealt > 0.0 && semantic.drainFraction() > 0.0) {
         player.heal((float)(dealt * semantic.drainFraction()));
      }

      if (dealt > 0.0 && semantic.recoilFraction() > 0.0) {
         player.setHealth(Math.max(0.0F, player.getHealth() - (float)(dealt * semantic.recoilFraction())));
      }
   }

   private void applyMoveAttack(ServerPlayerEntity player, SkillMetadata metadata, LivingEntity target, double amount, MoveTemplate move) {
      UUID previous = this.executingMoveDamage.get();
      this.executingMoveDamage.set(player.getUuid());

      try {
         metadata.attack(
            target,
            amount,
            new DamageType[]{DamageType.SKILL, move.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL() ? DamageType.PHYSICAL : DamageType.MAGIC}
         );
      } finally {
         if (previous == null) {
            this.executingMoveDamage.remove();
         } else {
            this.executingMoveDamage.set(previous);
         }
      }
   }

   public boolean isExecutingMoveDamage(ServerPlayerEntity player) {
      return player != null && player.getUuid().equals(this.executingMoveDamage.get());
   }

   public boolean blocksDamage(LivingEntity entity) {
      return entity != null && this.realtime.protectedNow(entity.getUuid(), SVFrameMMO.currentTick());
   }

   public void grantProtection(LivingEntity entity, long ticks) {
      if (entity != null && ticks > 0L) {
         this.realtime.protect(entity.getUuid(), SVFrameMMO.currentTick(), ticks);
      }
   }

   public boolean isVisualEntityOf(UUID player, UUID entity) {
      FusionSession session = player == null ? null : this.byPlayer.get(player);
      return session != null && session.activated() && entity != null && entity.equals(session.deployedEntityUuid());
   }

   private boolean rollAccuracy(ServerPlayerEntity caster, LivingEntity target, MoveTemplate move, long tick) {
      return ThreadLocalRandom.current().nextDouble() < this.realtime.accuracyChance(caster.getUuid(), target.getUuid(), move.getAccuracy(), tick);
   }

   public void applyMoveStage(LivingEntity entity, BattleStat stat, int amount) {
      if (entity != null) {
         int stage = this.realtime.add(entity.getUuid(), stat, amount, SVFrameMMO.currentTick(), 600L);
         if (stat == BattleStat.SPEED) {
            EntityAttributeInstance attribute = entity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (attribute != null) {
               attribute.removeModifier(MOVE_SPEED_MODIFIER);
               if (stage != 0) {
                  attribute.addTemporaryModifier(new EntityAttributeModifier(MOVE_SPEED_MODIFIER, BattleStat.multiplier(stage) - 1.0, Operation.ADD_MULTIPLIED_TOTAL));
                  this.moveSpeedSubjects.put(entity.getUuid(), entity);
               } else {
                  this.moveSpeedSubjects.remove(entity.getUuid());
               }
            }
         }
      }
   }

   public int moveStage(LivingEntity entity, BattleStat stat) {
      return this.realtime.stage(entity.getUuid(), stat, SVFrameMMO.currentTick());
   }

   public void clearMoveState(UUID entity) {
      this.realtime.clear(entity);
      this.removeMoveSpeed(entity);
   }

   public void clearMoveStates() {
      this.realtime.clear();

      for (UUID entity : List.copyOf(this.moveSpeedSubjects.keySet())) {
         this.removeMoveSpeed(entity);
      }
   }

   private void removeMoveSpeed(UUID entity) {
      LivingEntity subject = this.moveSpeedSubjects.remove(entity);
      if (subject != null) {
         EntityAttributeInstance attribute = subject.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
         if (attribute != null) {
            attribute.removeModifier(MOVE_SPEED_MODIFIER);
         }
      }
   }

   public boolean rollMoveAccuracy(ServerPlayerEntity caster, LivingEntity target, MoveTemplate move) {
      return this.rollAccuracy(caster, target, move, SVFrameMMO.currentTick());
   }

   public double moveDamageMultiplier(ServerPlayerEntity caster, LivingEntity target, MoveTemplate move) {
      return this.realtime
         .damageMultiplier(
            caster.getUuid(), target.getUuid(), move.getDamageCategory() == DamageCategories.INSTANCE.getPHYSICAL(), SVFrameMMO.currentTick()
         );
   }

   private static double rawMoveDamage(SkillMetadata metadata, FusionService.MoveCast cast, MoveTemplate move) {
      double configuredDamage = Math.max(0.0, metadata.getParameter("damage"));
      double base = configuredDamage > 0.0 ? configuredDamage : Math.max(1.0, move.getPower() / 10.0);
      return Math.max(1.0, base * cast.session().bonusMultiplier());
   }

   private static void applyStatus(LivingEntity target, MoveSemantic.Status status) {
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
            target.setFrozenTicks(Math.max(target.getFrozenTicks(), 100));
            break;
         case CONFUSION:
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
            break;
         case FLINCH:
            target.setVelocity(Vec3d.ZERO);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 5));
         case NONE:
      }
   }

   private static String safeMessage(RuntimeException error) {
      String message = error.getMessage();
      return message != null && !message.isBlank() ? message : error.getClass().getSimpleName();
   }

   public record EndResult(FusionSession session, String rejection) {
      public static FusionService.EndResult rejected(String reason) {
         return new FusionService.EndResult(null, reason);
      }

      public boolean success() {
         return this.session != null;
      }
   }

   public record MoveCast(FusionSession session, Pokemon pokemon, Move move) {
   }

   public record StartResult(FusionSession session, String rejection) {
      public static FusionService.StartResult rejected(String reason) {
         return new FusionService.StartResult(null, reason);
      }

      public boolean success() {
         return this.session != null;
      }
   }
}
