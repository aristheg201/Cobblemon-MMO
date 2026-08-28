package vn.svframe.svframemmo.cobblemon.move;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.battles.MoveTarget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframemmo.cobblemon.fusion.FusionService;

import java.util.Comparator;
import java.util.List;

/** One shared SVFrameMMO skill handler for a Cobblemon move definition. */
public final class CobblemonMoveSkill extends SkillHandler<CobblemonMoveSkill.Result> {
    private static final double TARGET_RANGE = 18.0d;
    private final MoveTemplate move;
    private final MoveSemantic semantic;
    private final FusionService fusions;
    private final String moveId;

    public CobblemonMoveSkill(MoveTemplate move, MoveSemantic semantic, FusionService fusions) {
        super("COBBLEMON_MOVE_" + CobblemonMoveSkillAdapter.id(move.getName()));
        this.move = move;
        this.semantic = semantic;
        this.fusions = fusions;
        this.moveId = CobblemonMoveSkillAdapter.id(move.getName());
    }

    @Override public String getName() { return move.getDisplayName().getString(); }

    @Override
    public Result getResult(SkillMetadata metadata) {
        ServerPlayerEntity player = metadata.getCaster().getData().getPlayer();
        FusionService.MoveCast cast = fusions.prepareMoveCast(player, moveId);
        if (cast == null) return Result.failed();
        LivingEntity target = requiresTarget() ? acquireTarget(player, cast.session().deployedEntityUuid()) : null;
        if (requiresTarget() && target == null) return Result.failed();
        return new Result(true, cast, target);
    }

    @Override
    public void whenCast(Result result, SkillMetadata metadata) {
        if (!result.success || result.cast == null) return;
        if (!fusions.consumePp(result.cast)) return;
        fusions.executeMove(result.cast, result.target, semantic, metadata);
    }

    private boolean requiresTarget() {
        if (move.getTarget() == MoveTarget.self || semantic.protect() || semantic.healFraction() > 0d) return false;
        if (move.getPower() > 0d) return true;
        if (semantic.status() != MoveSemantic.Status.NONE) return true;
        return semantic.stages().stream().anyMatch(change -> change.target() == MoveSemantic.Target.TARGET);
    }

    private static LivingEntity acquireTarget(ServerPlayerEntity player, java.util.UUID excludedEntity) {
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Box search = player.getBoundingBox().stretch(look.multiply(TARGET_RANGE)).expand(2.0d);
        List<Entity> candidates = player.getWorld().getOtherEntities(player, search, entity ->
                entity instanceof LivingEntity living && living.isAlive() && !entity.getUuid().equals(excludedEntity));
        return candidates.stream()
                .map(entity -> (LivingEntity) entity)
                .filter(entity -> {
                    Vec3d delta = entity.getBoundingBox().getCenter().subtract(eye);
                    return delta.lengthSquared() > 0.0001d && delta.normalize().dotProduct(look) >= 0.92d;
                })
                .min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(player)))
                .orElse(null);
    }

    public static final class Result implements SkillResult {
        private final boolean success;
        private final FusionService.MoveCast cast;
        private final LivingEntity target;
        private Result(boolean success, FusionService.MoveCast cast, LivingEntity target) {
            this.success = success; this.cast = cast; this.target = target;
        }
        public static Result failed() { return new Result(false, null, null); }
        @Override public boolean isSuccessful() { return success; }
    }
}
