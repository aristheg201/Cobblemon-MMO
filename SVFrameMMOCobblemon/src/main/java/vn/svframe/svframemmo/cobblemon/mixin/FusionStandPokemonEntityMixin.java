package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes Cobblemon's local entity-pushing behaviour from packet-only Fusion Stand entities. */
@Mixin(PokemonEntity.class)
public abstract class FusionStandPokemonEntityMixin {
    private static final int STAND_ENTITY_BASE = 1_000_000_000;

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void svframe$standIsNeverPushable(CallbackInfoReturnable<Boolean> cir) {
        if (((PokemonEntity) (Object) this).getId() >= STAND_ENTITY_BASE) cir.setReturnValue(false);
    }

    @Inject(method = "tickCramming", at = @At("HEAD"), cancellable = true)
    private void svframe$standNeverPushesNearbyEntities(CallbackInfo ci) {
        if (((PokemonEntity) (Object) this).getId() >= STAND_ENTITY_BASE) ci.cancel();
    }
}
