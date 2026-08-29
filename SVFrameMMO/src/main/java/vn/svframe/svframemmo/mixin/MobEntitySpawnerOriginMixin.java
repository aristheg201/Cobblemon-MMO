package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.experience.source.NativeExperienceRuntime;

@Mixin(MobEntity.class)
public abstract class MobEntitySpawnerOriginMixin {
    @Inject(method = "initialize", at = @At("HEAD"))
    private void svframemmo$spawnOrigin(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason,
                                        EntityData entityData, CallbackInfoReturnable<EntityData> cir) {
        if (spawnReason == SpawnReason.SPAWNER && SVFrameMMO.config().preventSpawnerXp())
            ((NativeExperienceRuntime.SpawnerTracked) this).svframemmo$setFromSpawner(true);
    }
}
