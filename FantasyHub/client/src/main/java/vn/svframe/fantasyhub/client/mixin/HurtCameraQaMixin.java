package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.ClientFeedbackQa;

@Mixin(GameRenderer.class)
public abstract class HurtCameraQaMixin {
    @Inject(method="tiltViewWhenHurt",at=@At("HEAD"))
    private void fantasyhub$observeCamera(MatrixStack matrices,float delta,CallbackInfo ci){
        var player=MinecraftClient.getInstance().player;
        if(ClientFeedbackQa.ENABLED&&player!=null&&player.hurtTime>0)ClientFeedbackQa.hurtCameraFrames++;
    }
}
