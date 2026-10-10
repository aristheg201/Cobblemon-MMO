package vn.svframe.svframelib.fabric.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframelib.fabric.HeroEntranceRuntime;

/** Vanilla flight enforcement stays enabled; only this server-owned movement is exempt. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class HeroEntranceFloatingMixin {
    @Shadow public ServerPlayerEntity player;
    @Shadow private boolean floating;
    @Shadow private int floatingTicks;

    @Inject(method = "tick", at = @At("HEAD"))
    private void svframe$heroMovement(CallbackInfo callback) {
        if (HeroEntranceRuntime.protectsFall(player)) {
            floating = false;
            floatingTicks = 0;
        }
    }
}
