package vn.svframe.svframemmo.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import vn.svframe.svframemmo.experience.source.NativeExperienceRuntime;

@Mixin(LivingEntity.class)
public abstract class LivingEntitySpawnerOriginMixin implements NativeExperienceRuntime.SpawnerTracked {
    @Unique private boolean svframemmo$fromSpawner;
    @Override public boolean svframemmo$fromSpawner() { return svframemmo$fromSpawner; }
    @Override public void svframemmo$setFromSpawner(boolean value) { svframemmo$fromSpawner = value; }
}
