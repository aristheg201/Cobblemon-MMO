package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;

import java.util.Comparator;
import java.util.List;

/** Fusion-only SVFrameMMO handler backed by one of the Pokemon's active Cobblemon moves. */
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
        if (fusion == null) return Result.failed();
        MoveTemplate move = fusion.move().getTemplate();
        CobblemonMoveProfile profile = CobblemonMoveProfile.of(move);
        MoveSemantic semantic = semantics.resolve(move);
        LivingEntity target = profile.requiresSingleTarget() || semanticNeedsTarget(move, semantic)
                ? acquireTarget(player, fusion.session().deployedEntityUuid(), profile.range()) : null;
        if ((profile.requiresSingleTarget() || semanticNeedsTarget(move, semantic)) && target == null) return Result.failed();
        return new Result(true, fusion, move, profile, semantic, target);
    }

    @Override
    public void whenCast(Result result, SkillMetadata metadata) {
        if (!result.success || result.fusion == null) return;
        ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
        if (!fusions.consumePp(result.fusion)) return;
        fusions.executeMove(result.fusion, result.target, result.semantic, metadata);
        if (result.target != null)
            SVFrameMMOCobblemon.moveVfx().renderImpact(player, result.move, result.target.getBoundingBox().getCenter());
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
