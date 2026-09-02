package vn.svframe.svframemmo.cobblemon.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.cobblemon.fusion.FusionVisualBridge;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionRawPacketSender;

/** Rewrites only play-state entity packets belonging to an active Fusion session. */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin implements FusionRawPacketSender {
    @Shadow @Final protected ClientConnection connection;

    @Inject(
            method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void svframe$rewriteFusionPackets(Packet<?> packet, PacketCallbacks callbacks, CallbackInfo ci) {
        if (!((Object) this instanceof PlayerAssociatedNetworkHandler play)) return;
        if (FusionVisualBridge.rewriteOutgoing(play.getPlayer(), packet)) ci.cancel();
    }

    @Override
    public void svframe$sendRaw(Packet<?> packet) {
        connection.send(packet);
    }
}
