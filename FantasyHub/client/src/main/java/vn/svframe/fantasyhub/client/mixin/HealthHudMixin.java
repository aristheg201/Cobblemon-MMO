package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.FantasyHubClient;

/** Hide duplicate hearts at rendering time; food, oxygen, armor and attributes stay intact. */
@Mixin(InGameHud.class)
public abstract class HealthHudMixin {
    @Inject(method="renderHealthBar",at=@At("HEAD"),cancellable=true)
    private void fantasyhub$customHealthPresentation(CallbackInfo ci){
        if(FantasyHubClient.hudEnabled&&FantasyHubClient.state().has("schema"))ci.cancel();
    }
    @ModifyArgs(method="renderStatusBars",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/hud/InGameHud;renderArmor(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIII)V"))
    private void fantasyhub$armorLayout(Args args){
        // Hidden vanilla hearts must not reserve seven empty rows for a
        // high-health character and push armor into the ability bar.
        if(FantasyHubClient.hudEnabled&&FantasyHubClient.state().has("schema"))args.set(3,1);
    }
}
