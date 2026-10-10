package vn.svframe.fantasyhub.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.client.ClientFeedbackQa;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class DamageFeedbackQaMixin {
    @Inject(method="onEntityDamage",at=@At("TAIL"))
    private void fantasyhub$observeDamage(EntityDamageS2CPacket packet,CallbackInfo ci){
        var player=MinecraftClient.getInstance().player;
        if(ClientFeedbackQa.ENABLED&&player!=null&&player.getId()==packet.entityId())ClientFeedbackQa.damagePackets++;
    }
}
