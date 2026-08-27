package vn.svframe.svframelib.fabric.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframelib.fabric.PassiveSkillRuntime;
import vn.svframe.svframelib.fabric.runtime.ProjectilePassiveSnapshotHolder;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntitySnapshotMixin implements ProjectilePassiveSnapshotHolder {
    @Unique
    private PassiveSkillRuntime.Snapshot svframelib$passiveSnapshot;

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void svframelib$capturePassiveSnapshot(Entity owner, CallbackInfo ci) {
        if (!((Object) this instanceof PersistentProjectileEntity)) return;
        if (owner instanceof ServerPlayerEntity player) {
            svframelib$passiveSnapshot = PassiveSkillRuntime.snapshot(player.getUuid());
        } else {
            svframelib$passiveSnapshot = null;
        }
    }

    @Override
    public PassiveSkillRuntime.Snapshot svframelib$getPassiveSnapshot() {
        return svframelib$passiveSnapshot;
    }

    @Override
    public void svframelib$setPassiveSnapshot(PassiveSkillRuntime.Snapshot snapshot) {
        svframelib$passiveSnapshot = snapshot;
    }
}
