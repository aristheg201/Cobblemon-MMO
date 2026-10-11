package vn.svframe.skript.fabric.mixin;
import net.minecraft.entity.player.*;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.SkriptDeathState;
/** Runs before the selective SVFrameItems retention path, so retained item UUIDs are never copied twice. */
@Mixin(value=PlayerInventory.class,priority=2000)
public abstract class InventoryDeathMixin {
 @Shadow @Final public PlayerEntity player;
 @Inject(method="dropAll",at=@At("HEAD"),cancellable=true)
 private void svframe$keep(CallbackInfo ci){if(player instanceof ServerPlayerEntity&&((SkriptDeathState)player).svframe$keepInventory())ci.cancel();else if(player instanceof ServerPlayerEntity&&((SkriptDeathState)player).svframe$cancelDrops()){((PlayerInventory)(Object)this).clear();ci.cancel();}}
}
