package vn.svframe.svframelib.fabric.mixin;

import vn.svframe.svframelib.entity.ProjectileMetadata;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframelib.fabric.LegacyTriggerType;
import vn.svframe.svframelib.fabric.PassiveSkillRuntime;
import vn.svframe.svframelib.fabric.runtime.ProjectilePassiveSnapshotHolder;

import java.util.LinkedHashMap;
import java.util.Map;

/** Native projectile lifecycle triggers matching SVFrameLib's arrow/trident trigger family. */
@Mixin(PersistentProjectileEntity.class)
public abstract class PersistentProjectileTriggerMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void svframelib$projectileTick(CallbackInfo ci) {
        PersistentProjectileEntity projectile = self();
        if (projectile.getWorld().isClient) return;

        ProjectileMetadata metadata = ProjectileMetadata.get(projectile);
        if (metadata != null) {
            metadata.triggerTick();
            return;
        }

        ServerPlayerEntity owner = playerOwner(projectile);
        if (owner == null) return;
        fireSnapshot(projectile, owner, trigger("TICK"), owner.getUuid(), projectileContext(projectile));
    }

    @Inject(method = "onEntityHit", at = @At("TAIL"))
    private void svframelib$projectileHit(EntityHitResult hit, CallbackInfo ci) {
        PersistentProjectileEntity projectile = self();
        if (projectile.getWorld().isClient) return;

        Entity target = hit.getEntity();
        ProjectileMetadata metadata = ProjectileMetadata.get(projectile);
        if (metadata != null) {
            metadata.triggerHit(target);
            metadata.unregisterOnHit(projectile);
            return;
        }

        ServerPlayerEntity owner = playerOwner(projectile);
        if (owner == null) return;
        Map<String, Object> context = projectileContext(projectile);
        context.put("target-uuid", target.getUuidAsString());
        context.put("target-type", target.getType().toString());
        fireSnapshot(projectile, owner, trigger("HIT"), target.getUuid(), context);
    }

    @Inject(method = "onBlockHit", at = @At("TAIL"))
    private void svframelib$projectileLand(BlockHitResult hit, CallbackInfo ci) {
        PersistentProjectileEntity projectile = self();
        if (projectile.getWorld().isClient) return;

        ProjectileMetadata metadata = ProjectileMetadata.get(projectile);
        if (metadata != null) {
            metadata.triggerLand(projectile);
            metadata.unregisterOnHit(projectile);
            return;
        }

        ServerPlayerEntity owner = playerOwner(projectile);
        if (owner == null) return;
        Map<String, Object> context = projectileContext(projectile);
        context.put("block-x", hit.getBlockPos().getX());
        context.put("block-y", hit.getBlockPos().getY());
        context.put("block-z", hit.getBlockPos().getZ());
        context.put("face", hit.getSide().name());
        fireSnapshot(projectile, owner, trigger("LAND"), owner.getUuid(), context);
    }

    private static void fireSnapshot(PersistentProjectileEntity projectile,
                                     ServerPlayerEntity owner,
                                     LegacyTriggerType trigger,
                                     java.util.UUID target,
                                     Map<String, ?> context) {
        ProjectilePassiveSnapshotHolder holder = (ProjectilePassiveSnapshotHolder) projectile;
        PassiveSkillRuntime.Snapshot snapshot = holder.svframelib$getPassiveSnapshot();
        if (snapshot == null) {
            snapshot = PassiveSkillRuntime.snapshot(owner.getUuid());
            holder.svframelib$setPassiveSnapshot(snapshot);
        }
        PassiveSkillRuntime.fireSnapshot(snapshot, trigger, target, context);
    }

    private LegacyTriggerType trigger(String suffix) {
        return LegacyTriggerType.parse((self() instanceof TridentEntity ? "TRIDENT_" : "ARROW_") + suffix);
    }

    private PersistentProjectileEntity self() {
        return (PersistentProjectileEntity) (Object) this;
    }

    private static ServerPlayerEntity playerOwner(PersistentProjectileEntity projectile) {
        Entity owner = projectile.getOwner();
        if (!(owner instanceof ServerPlayerEntity player)) return null;
        if (player.getServer() == null) return null;
        return player.getServer().getPlayerManager().getPlayer(player.getUuid()) == player ? player : null;
    }

    private static Map<String, Object> projectileContext(PersistentProjectileEntity projectile) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("projectile-uuid", projectile.getUuidAsString());
        out.put("projectile-type", projectile.getType().toString());
        out.put("projectile-x", projectile.getX());
        out.put("projectile-y", projectile.getY());
        out.put("projectile-z", projectile.getZ());
        out.put("projectile-velocity-x", projectile.getVelocity().x);
        out.put("projectile-velocity-y", projectile.getVelocity().y);
        out.put("projectile-velocity-z", projectile.getVelocity().z);
        return out;
    }
}
