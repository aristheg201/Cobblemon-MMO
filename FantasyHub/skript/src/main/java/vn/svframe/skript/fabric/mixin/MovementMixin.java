package vn.svframe.skript.fabric.mixin;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.*;
import vn.svframe.skript.lang.Values.Location;
@Mixin(ServerPlayNetworkHandler.class)
public abstract class MovementMixin {
 @Shadow public ServerPlayerEntity player;
 @Unique private Location svframe$before;
 @Inject(method="onPlayerMove",at=@At(value="INVOKE",target="Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",shift=At.Shift.AFTER))
 private void svframe$beforeMove(PlayerMoveC2SPacket packet,CallbackInfo ci){svframe$before=FabricPlatform.location(player);}
 @Inject(method="onPlayerMove",at=@At("TAIL"))
 private void svframe$afterMove(PlayerMoveC2SPacket packet,CallbackInfo ci){if(svframe$before==null)return;Location from=svframe$before;svframe$before=null;Location to=FabricPlatform.location(player);if(from.equals(to))return;var event=SVFrameSkript.playerEvent(player);event.put("from",from);event.put("to",to);SVFrameSkript.dispatch("playermoveevent",event);}
}
