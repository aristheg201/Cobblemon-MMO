package vn.svframe.skript.fabric.mixin;
import java.util.*;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.*;
/** Observe validated server packets, including screen handlers overriding onSlotClick. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class InventoryMixin {
 @Shadow public ServerPlayerEntity player;
 @Inject(method="onClickSlot",at=@At(value="INVOKE",target="Lnet/minecraft/screen/ScreenHandler;onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V"),cancellable=true)
 private void svframe$inventory(ClickSlotC2SPacket packet,CallbackInfo ci){
  Map<String,Object> event=SVFrameSkript.playerEvent(player);event.put("clicked slot",(double)packet.getSlot());var handler=player.currentScreenHandler;
  event.put("inventory",new FabricPlatform.InventoryRef(FabricPlatform.ref(player),SVFrameSkript.screenTitle(player),handler.syncId));event.put("event-inventory",event.get("inventory"));event.put("clicked inventory",event.get("inventory"));
  if(packet.getSlot()>=0&&packet.getSlot()<handler.slots.size())event.put("event-item",new FabricPlatform.ItemValue(handler.slots.get(packet.getSlot()).getStack()));
  String type=packet.getActionType()==SlotActionType.QUICK_CRAFT?"inventory drag":"inventory click";
  if(Boolean.TRUE.equals(SVFrameSkript.dispatch(type,event).get("cancelled"))){handler.enableSyncing();handler.syncState();ci.cancel();}
 }
}
