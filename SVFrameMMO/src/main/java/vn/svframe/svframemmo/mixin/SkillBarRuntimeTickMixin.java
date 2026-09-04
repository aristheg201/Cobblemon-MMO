package vn.svframe.svframemmo.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.skill.runtime.SkillBarRuntime;

import java.util.Map;

/** Avoids scanning every online player on idle ticks when no casting session needs 20 Hz processing. */
@Mixin(value = SkillBarRuntime.class, remap = false)
public abstract class SkillBarRuntimeTickMixin {
    @Shadow(remap = false) @Final private Map<?, ?> sessions;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void svframemmo$skipIdleSkillBarTicks(long tick, CallbackInfo ci) {
        if (!sessions.isEmpty()) return;
        var actionBar = SVFrameMMO.config().actionBar();
        if (!actionBar.enabled() || tick % Math.max(1L, actionBar.updateTicks()) != 0L) ci.cancel();
    }
}
