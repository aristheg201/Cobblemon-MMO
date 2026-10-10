package vn.svframe.svframemmo.mixin;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframelib.fabric.SVFrameLibStatMod;
import vn.svframe.svframemmo.SVFrameMMO;

/** Vanilla has loaded entity NBT and created the network handler at this point.
 * Install persisted class/attribute/equipment inputs before any play packet is sent. */
@Mixin(PlayerManager.class)
public abstract class PlayerManagerStatLoginMixin {
    @Inject(method="onPlayerConnect", at=@At(value="INVOKE",
            target="Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V", ordinal=0))
    private void svframe$restoreStatsBeforeLogin(ClientConnection connection, ServerPlayerEntity player,
                                               ConnectedClientData clientData, CallbackInfo ci) {
        SVFrameLibStatMod.bindPlayer(player);
        SVFrameMMO.playerData().join(player);
    }
}
