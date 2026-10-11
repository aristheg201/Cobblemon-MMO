package vn.svframe.skript.fabric.mixin;
import java.util.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.*;
import vn.svframe.skript.lang.Values;
@Mixin(ServerPlayerEntity.class)
public abstract class PlayerMixin {
 @Inject(method="onHandledScreenClosed",at=@At("HEAD"))
 private void svframe$close(CallbackInfo ci){var player=(ServerPlayerEntity)(Object)this;Map<String,Object> event=SVFrameSkript.playerEvent(player);event.put("event-inventory",new FabricPlatform.InventoryRef(FabricPlatform.ref(player),SVFrameSkript.screenTitle(player),player.currentScreenHandler.syncId));SVFrameSkript.dispatch("inventory close",event);}
 @Inject(method="onHandledScreenClosed",at=@At("RETURN"))
 private void svframe$closed(CallbackInfo ci){SVFrameSkript.screenTitle((ServerPlayerEntity)(Object)this,"Inventory");}
 @Inject(method="openHandledScreen",at=@At("HEAD"))
 private void svframe$open(net.minecraft.screen.NamedScreenHandlerFactory factory,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.OptionalInt> ci){if(factory!=null)SVFrameSkript.screenTitle((ServerPlayerEntity)(Object)this,factory.getDisplayName().getString());}
 @Inject(method="teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V",at=@At("HEAD"),cancellable=true)
 private void svframe$teleport(ServerWorld world,double x,double y,double z,float yaw,float pitch,CallbackInfo ci){Map<String,Object> event=SVFrameSkript.playerEvent((ServerPlayerEntity)(Object)this);event.put("destination",new Values.Location(FabricPlatform.ref(world),x,y,z,yaw,pitch));if(Boolean.TRUE.equals(SVFrameSkript.dispatch("teleport",event).get("cancelled")))ci.cancel();}
 @Inject(method="onDeath",at=@At("HEAD"))
 private void svframe$death(DamageSource source,CallbackInfo ci){var player=(ServerPlayerEntity)(Object)this;Map<String,Object> event=SVFrameSkript.playerEvent(player);event.put("victim",FabricPlatform.ref(player));if(source.getAttacker()!=null)event.put("attacker",FabricPlatform.ref(source.getAttacker()));var state=SVFrameSkript.dispatch("death of player",event);var decision=(SkriptDeathState)player;decision.svframe$keepInventory(Boolean.TRUE.equals(state.get("keep inventory")));decision.svframe$cancelDrops(Boolean.TRUE.equals(state.get("cancel drops")));decision.svframe$deathMessage((String)state.get("death message"));}
 @ModifyArg(method="onDeath",at=@At(value="INVOKE",target="Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/text/Text;Z)V"),index=0)
 private net.minecraft.text.Text svframe$deathBroadcast(net.minecraft.text.Text original){String message=((SkriptDeathState)this).svframe$deathMessage();return message==null?original:net.minecraft.text.Text.literal(message.replace('&','§'));}
 @ModifyArg(method="onDeath",at=@At(value="INVOKE",target="Lnet/minecraft/server/PlayerManager;sendToTeam(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/text/Text;)V"),index=1)
 private net.minecraft.text.Text svframe$deathTeam(net.minecraft.text.Text original){return svframe$deathBroadcast(original);}
 @ModifyArg(method="onDeath",at=@At(value="INVOKE",target="Lnet/minecraft/server/PlayerManager;sendToOtherTeams(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/text/Text;)V"),index=1)
 private net.minecraft.text.Text svframe$deathOtherTeam(net.minecraft.text.Text original){return svframe$deathBroadcast(original);}
}
