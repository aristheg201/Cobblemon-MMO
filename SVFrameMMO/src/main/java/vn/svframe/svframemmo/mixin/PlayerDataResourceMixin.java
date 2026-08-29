package vn.svframe.svframemmo.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;

@Mixin(PlayerData.class)
public abstract class PlayerDataResourceMixin {
    @Unique private double svframemmo$oldResource;
    @Unique private PlayerResource svframemmo$resource;

    @Inject(method = "setResource", at = @At("HEAD"))
    private void svframemmo$captureResource(PlayerResource resource, double amount, ResourceUpdateReason reason,
                                             CallbackInfoReturnable<Boolean> cir) {
        PlayerData self = (PlayerData) (Object) this;
        svframemmo$resource = resource;
        svframemmo$oldResource = self.getResource(resource);
    }

    @Inject(method = "setResource", at = @At("RETURN"))
    private void svframemmo$resourceCommitted(PlayerResource resource, double amount, ResourceUpdateReason reason,
                                               CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() || svframemmo$resource != resource) return;
        PlayerData self = (PlayerData) (Object) this;
        SVFrameMMO.nativeExperience().onResourceCommitted(self, resource, svframemmo$oldResource, self.getResource(resource));
    }
}
