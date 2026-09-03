package vn.svframe.svframemmo.cobblemon.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticDefinition;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticEmitterMetadata;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticService;

import java.nio.file.Path;

/** Registers per-layer particle backend metadata without changing cosmetic persistence or slot semantics. */
@Mixin(value = CosmeticService.class, remap = false)
public abstract class CosmeticServiceEmitterMixin {
    @Inject(method = "reloadDefinitions", at = @At("HEAD"), remap = false)
    private void svframe$clearEmitterMetadata(CallbackInfo ci) {
        CosmeticEmitterMetadata.clear();
    }

    @Inject(method = "parse", at = @At("RETURN"), remap = false)
    private void svframe$registerEmitterMetadata(Path file,
                                                  CallbackInfoReturnable<CosmeticDefinition> cir) {
        CosmeticDefinition definition = cir.getReturnValue();
        if (definition != null) CosmeticEmitterMetadata.register(file, definition);
    }
}
