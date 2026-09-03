package vn.svframe.svframemmo.cobblemon.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes packet-only Fusion Stand IDs non-solid and non-targetable on the client without touching real entities. */
@Mixin(Entity.class)
public abstract class FusionStandEntityMixin {
    private static final int STAND_ENTITY_BASE = 1_000_000_000;

    @Shadow public abstract int getId();

    @Inject(method = "isCollidable", at = @At("HEAD"), cancellable = true)
    private void svframe$standIsNeverCollidable(CallbackInfoReturnable<Boolean> cir) {
        if (getId() >= STAND_ENTITY_BASE) cir.setReturnValue(false);
    }

    @Inject(method = "collidesWith", at = @At("HEAD"), cancellable = true)
    private void svframe$standNeverCollidesWith(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (getId() >= STAND_ENTITY_BASE || (other != null && other.getId() >= STAND_ENTITY_BASE))
            cir.setReturnValue(false);
    }

    @Inject(method = "canHit", at = @At("HEAD"), cancellable = true)
    private void svframe$standCannotBeTargeted(CallbackInfoReturnable<Boolean> cir) {
        if (getId() >= STAND_ENTITY_BASE) cir.setReturnValue(false);
    }
}
