package vn.svframe.svframemmo.cobblemon.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.cobblemon.fusion.FusionVisualBridge;
import vn.svframe.svframemmo.cobblemon.fusion.render.FusionRawPacketSender;

/** Rewrites only play-state entity packets belonging to an active Fusion session. */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin implements FusionRawPacketSender {
    @Shadow @Final protected ClientConnection connection;

    @Unique
    private boolean svframe$selfEquipmentGuardActive;

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
        if (packet instanceof EntityEquipmentUpdateS2CPacket equipment
                && (Object) this instanceof PlayerAssociatedNetworkHandler play
                && equipment.getEntityId() == play.getPlayer().getId()) {
            boolean clearsEverySlot = !equipment.getEquipmentList().isEmpty()
                    && equipment.getEquipmentList().stream().allMatch(pair -> pair.getSecond().isEmpty());
            if (clearsEverySlot) {
                // Fusion used to send an all-empty equipment packet to the local ClientPlayerEntity in order to hide
                // armor/held items in third person. ClientPlayNetworkHandler applies that packet through equipStack(),
                // which mutates the local player's inventory representation. Never send such a packet to its owner.
                // The real player is already hidden by self-only invisibility metadata/team rules; remote viewers still
                // have their equipment packets suppressed by FusionVisualBridge while they render the Pokemon disguise.
                if (!svframe$selfEquipmentGuardActive) {
                    svframe$selfEquipmentGuardActive = true;
                    play.getPlayer().playerScreenHandler.syncState();
                    if (play.getPlayer().currentScreenHandler != play.getPlayer().playerScreenHandler)
                        play.getPlayer().currentScreenHandler.syncState();
                }
                return;
            }
            svframe$selfEquipmentGuardActive = false;
        }
        connection.send(packet);
    }
}
