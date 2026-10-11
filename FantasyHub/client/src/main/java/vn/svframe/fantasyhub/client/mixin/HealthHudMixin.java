package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.FantasyHubClient;
import vn.svframe.fantasyhub.client.CompactHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Hide duplicate hearts at rendering time; food, oxygen, armor and attributes stay intact. */
@Mixin(InGameHud.class)
public abstract class HealthHudMixin {
    @Inject(method="renderHealthBar",at=@At("HEAD"),cancellable=true)
    private void fantasyhub$customHealthPresentation(CallbackInfo ci){
        if(CompactHud.visible("health"))ci.cancel();
    }
    @Inject(method="renderStatusBars",at=@At("TAIL"))
    private void fantasyhub$resources(DrawContext context,CallbackInfo ci){
        // Render with vanilla status bars so chat and menus keep the same HUD
        // visibility and foreground ordering as hearts and food.
        if(CompactHud.active())CompactHud.render(context);
    }
    @ModifyArgs(method="renderStatusBars",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/hud/InGameHud;renderArmor(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/entity/player/PlayerEntity;IIII)V"))
    private void fantasyhub$armorLayout(Args args){
        // Hidden vanilla hearts must not reserve seven empty rows for a
        // high-health character and push armor into the ability bar.
        if(CompactHud.visible("health"))args.set(3,1);
        if(CompactHud.visible("stamina"))args.set(2,(int)args.get(2)-10);
    }
    @Inject(method="renderExperienceBar",at=@At("HEAD"),cancellable=true)
    private void fantasyhub$experience(DrawContext context,int x,CallbackInfo ci){if(CompactHud.active()){CompactHud.experience(context,x);ci.cancel();}}
    @Inject(method="renderExperienceLevel",at=@At("HEAD"),cancellable=true)
    private void fantasyhub$level(DrawContext context,RenderTickCounter counter,CallbackInfo ci){if(CompactHud.active()){CompactHud.level(context);ci.cancel();}}
    @ModifyArg(method="renderStatusBars",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V"),index=2)
    private int fantasyhub$oxygenAboveMana(int y){return CompactHud.visible("mana")?y-10:y;}
}
