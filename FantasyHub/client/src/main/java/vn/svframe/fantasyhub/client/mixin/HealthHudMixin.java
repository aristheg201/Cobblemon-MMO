package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.FantasyHubClient;

/** Hide duplicate hearts at rendering time; food, oxygen, armor and attributes stay intact. */
@Mixin(InGameHud.class)
public abstract class HealthHudMixin {
    @Inject(method="renderHealthBar",at=@At("HEAD"),cancellable=true)
    private void fantasyhub$customHealthPresentation(CallbackInfo ci){
        if(FantasyHubClient.hudEnabled&&FantasyHubClient.state().has("schema"))ci.cancel();
    }
}
