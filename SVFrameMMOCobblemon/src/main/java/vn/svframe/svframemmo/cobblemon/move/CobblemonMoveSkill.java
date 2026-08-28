package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Native SVFrameMMO handler backed by a live Cobblemon move definition. */
public final class CobblemonMoveSkill extends SkillHandler<CobblemonMoveSkill.Result> {
    private static final double TARGET_RANGE = 18.0d;
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

    public String moveId() { return moveId; }
    public String canonicalSkillId() { return CobblemonMoveSkillAdapter.canonicalId(moveId); }
    public MoveTemplate template() { return Moves.getByNameOrDummy(moveName); }
    @Override public String getName() { return template().getDisplayName().getString(); }

    @Override
    public Result getResult(SkillMetadata metadata) {
        ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
        FusionService.MoveCast fusion = fusions.prepareMoveCast(player, moveId);
        MoveTemplate move = fusion == null ? template() : fusion.move().getTemplate();
        CobblemonMoveProfile profile = CobblemonMoveProfile.of(move);
        MoveSemantic semantic = semantics.resolve(move);
        LivingEntity target = profile.requiresSingleTarget() || semanticNeedsTarget(move, semantic)
                ? acquireTarget(player, fusion == null ? null : fusion.session().deployedEntityUuid(), profile.range()) : null;
        if ((profile.requiresSingleTarget() || semanticNeedsTarget(move, semantic)) && target == null) return Result.failed();
        return new Result(true, fusion, move, profile, semantic, target);
    }

    @Override
    public void whenCast(Result result, SkillMetadata metadata) {
        if (!result.success) return;
        ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
        if (result.fusion != null) {
            if (!fusions.consumePp(result.fusion)) return;
            fusions.executeMove(result.fusion, result.target, result.semantic, metadata);
        } else {
            executeStandalone(player, result, metadata);
        }
        if (result.target != null)
            SVFrameMMOCobblemon.moveVfx().renderImpact(player, result.move, result.target.getBoundingBox().getCenter());
    }

    private void executeStandalone(ServerPlayerEntity player, Result result, SkillMetadata metadata) {
        MoveTemplate move = result.move;
        CobblemonMoveProfile profile = result.profile;
        MoveSemantic semantic = result.semantic;
        switch (profile.executor()) {
            case HEAL -> {
                double scale = 1d + Math.max(0, SVFrameMMO.playerData().get(player).getLevel() - 1) * 0.035d;
                player.heal((float) (profile.healBase() * scale));
                if (profile.cleanse()) cleanse(player);
                applySelfSemantic(player, semantic, 0d);
            }
            case SHIELD -> {
                float before = player.getAbsorptionAmount();
                float granted = 8f;
                player.setAbsorptionAmount(before + granted);
                fusions.grantProtection(player, 10L);
                SVFrameMMO.delayedActions().schedule(SVFrameMMO.currentTick() + 80L, () -> {
                    if (!player.isDisconnected()) player.setAbsorptionAmount(Math.max(before, player.getAbsorptionAmount() - granted));
                });
            }
            case WEATHER -> applyWeather(player.getServerWorld(), profile.weather());
            case TELEPORT -> teleportForward(player, 12d);
            case DASH -> {
                Vec3d look = player.getRotationVec(1f).normalize();
                Vec3d velocity = player.getVelocity().multiply(0.35d).add(look.multiply(profile.dashStrength())).add(0, 0.12d, 0);
                player.setVelocity(velocity);
                player.velocityModified = true;
            }
            case AOE -> executeAoe(player, move, profile, semantic, metadata);
            case SELF_BUFF -> applySelfSemantic(player, semantic, 0d);
            case TARGET_DEBUFF -> {
                if (result.target != null) applyTargetSemantic(result.target, semantic);
            }
            case TARGET, PROJECTILE -> executeDamage(player, result.target, move, profile, semantic, metadata);
        }
    }

    private void executeAoe(ServerPlayerEntity player, MoveTemplate move, CobblemonMoveProfile profile,
                            MoveSemantic semantic, SkillMetadata metadata) {
        Box box = player.getBoundingBox().expand(profile.radius());
        List<LivingEntity> targets = player.getServerWorld().getEntitiesByClass(LivingEntity.class, box,
                living -> living.isAlive() && living != player && !fusions.isVisualEntityOf(player.getUuid(), living.getUuid()));
        for (LivingEntity target : targets) executeDamage(player, target, move, profile, semantic, metadata);
    }

    private void executeDamage(ServerPlayerEntity player, LivingEntity target, MoveTemplate move,
                               CobblemonMoveProfile profile, MoveSemantic semantic, SkillMetadata metadata) {
        if (target == null || !rollAccuracy(move)) return;
        int hits = semantic.multiHitMin() == semantic.multiHitMax() ? semantic.multiHitMin()
                : ThreadLocalRandom.current().nextInt(semantic.multiHitMin(), semantic.multiHitMax() + 1);
        double scale = 1d + Math.max(0, SVFrameMMO.playerData().get(player).getLevel() - 1) * 0.025d;
        double perHit = Math.max(1d, profile.baseDamage() * scale);
        double dealt = 0d;
        DamageType category = profile.damageCategory().equals("physical") ? DamageType.PHYSICAL : DamageType.MAGIC;
        for (int i = 0; i < hits && target.isAlive(); i++) {
            metadata.attack(target, perHit, DamageType.SKILL, category);
            dealt += perHit;
        }
        applyTargetSemantic(target, semantic);
        applySelfSemantic(player, semantic, dealt);
    }

    private void applySelfSemantic(ServerPlayerEntity player, MoveSemantic semantic, double dealt) {
        for (MoveSemantic.StageChange change : semantic.stages()) {
            if (change.target() == MoveSemantic.Target.SELF) applyStage(player, change.stat(), change.stages());
        }
        if (semantic.healFraction() > 0d) player.heal((float) (player.getMaxHealth() * semantic.healFraction()));
        if (dealt > 0d && semantic.drainFraction() > 0d) player.heal((float) (dealt * semantic.drainFraction()));
        if (dealt > 0d && semantic.recoilFraction() > 0d)
            player.setHealth(Math.max(0f, player.getHealth() - (float) (dealt * semantic.recoilFraction())));
        if (semantic.protect()) fusions.grantProtection(player, 20L);
    }

    private void applyTargetSemantic(LivingEntity target, MoveSemantic semantic) {
        for (MoveSemantic.StageChange change : semantic.stages()) {
            if (change.target() == MoveSemantic.Target.TARGET) applyStage(target, change.stat(), change.stages());
        }
        if (semantic.status() != MoveSemantic.Status.NONE && ThreadLocalRandom.current().nextDouble() < semantic.statusChance())
            applyStatus(target, semantic.status());
    }

    private static void applyStage(LivingEntity entity, BattleStat stat, int stages) {
        if (stages == 0) return;
        int amplifier = Math.min(4, Math.max(0, Math.abs(stages) - 1));
        RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect = switch (stat) {
            case ATTACK, SPECIAL_ATTACK -> stages > 0 ? StatusEffects.STRENGTH : StatusEffects.WEAKNESS;
            case DEFENSE, SPECIAL_DEFENSE -> stages > 0 ? StatusEffects.RESISTANCE : StatusEffects.WEAKNESS;
            case SPEED, EVASION -> stages > 0 ? StatusEffects.SPEED : StatusEffects.SLOWNESS;
            case ACCURACY -> stages > 0 ? StatusEffects.LUCK : StatusEffects.BLINDNESS;
        };
        entity.addStatusEffect(new StatusEffectInstance(effect, 600, amplifier, false, false));
    }

    private static void applyStatus(LivingEntity target, MoveSemantic.Status status) {
        switch (status) {
            case PARALYSIS -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
            case BURN -> target.setOnFireFor(4f);
            case POISON -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 120, 0));
            case BAD_POISON -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 160, 1));
            case SLEEP -> {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 5));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
            }
            case FREEZE -> target.setFrozenTicks(Math.max(target.getFrozenTicks(), 100));
            case CONFUSION -> target.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
            case FLINCH -> {
                target.setVelocity(Vec3d.ZERO);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 12, 5));
            }
            case NONE -> { }
        }
    }

    private static void cleanse(ServerPlayerEntity player) {
        for (StatusEffectInstance instance : List.copyOf(player.getStatusEffects())) {
            if (!instance.getEffectType().value().isBeneficial()) player.removeStatusEffect(instance.getEffectType());
        }
    }

    private static void applyWeather(ServerWorld world, String weather) {
        if ("rain".equals(weather)) world.setWeather(0, 400, true, false);
        else world.setWeather(400, 0, false, false);
    }

    private static void teleportForward(ServerPlayerEntity player, double maxDistance) {
        Vec3d start = player.getPos();
        Vec3d look = player.getRotationVec(1f).normalize();
        ServerWorld world = player.getServerWorld();
        for (int step = (int) Math.floor(maxDistance); step >= 1; step--) {
            Vec3d candidate = start.add(look.multiply(step));
            BlockPos feet = BlockPos.ofFloored(candidate);
            BlockPos head = feet.up();
            BlockPos floor = feet.down();
            if (world.getBlockState(feet).isAir() && world.getBlockState(head).isAir() && !world.getBlockState(floor).isAir()) {
                player.requestTeleport(candidate.x, candidate.y, candidate.z);
                return;
            }
        }
    }

    private static boolean rollAccuracy(MoveTemplate move) {
        double accuracy = move.getAccuracy();
        return accuracy <= 0d || ThreadLocalRandom.current().nextDouble() < Math.max(0d, Math.min(1d, accuracy / 100d));
    }

    private static boolean semanticNeedsTarget(MoveTemplate move, MoveSemantic semantic) {
        if (move.getPower() > 0d) return false;
        return semantic.status() != MoveSemantic.Status.NONE || semantic.stages().stream()
                .anyMatch(change -> change.target() == MoveSemantic.Target.TARGET);
    }

    private static LivingEntity acquireTarget(ServerPlayerEntity player, java.util.UUID excludedEntity, double range) {
        double actualRange = range <= 0d ? TARGET_RANGE : range;
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Box search = player.getBoundingBox().stretch(look.multiply(actualRange)).expand(2.0d);
        List<Entity> candidates = player.getWorld().getOtherEntities(player, search, entity ->
                entity instanceof LivingEntity living && living.isAlive() && (excludedEntity == null || !entity.getUuid().equals(excludedEntity)));
        return candidates.stream().map(entity -> (LivingEntity) entity).filter(entity -> {
                    Vec3d delta = entity.getBoundingBox().getCenter().subtract(eye);
                    return delta.lengthSquared() > 0.0001d && delta.normalize().dotProduct(look) >= 0.92d;
                }).min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player))).orElse(null);
    }

    public static final class Result implements SkillResult {
        private final boolean success;
        private final FusionService.MoveCast fusion;
        private final MoveTemplate move;
        private final CobblemonMoveProfile profile;
        private final MoveSemantic semantic;
        private final LivingEntity target;

        private Result(boolean success, FusionService.MoveCast fusion, MoveTemplate move,
                       CobblemonMoveProfile profile, MoveSemantic semantic, LivingEntity target) {
            this.success = success;
            this.fusion = fusion;
            this.move = move;
            this.profile = profile;
            this.semantic = semantic;
            this.target = target;
        }

        static Result failed() { return new Result(false, null, null, null, null, null); }
        @Override public boolean isSuccessful() { return success; }
    }
}
