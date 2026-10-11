package vn.svframe.skript.fabric.mixin;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.SkriptDeathState;
/** Clone before SVFrameItems restores any previously pending retained overflow. */
@Mixin(value=ServerPlayerEntity.class,priority=2000)
public abstract class RespawnInventoryMixin {
 @Inject(method="copyFrom",at=@At("RETURN"))
 private void svframe$restore(ServerPlayerEntity old,boolean alive,CallbackInfo ci){var previous=(SkriptDeathState)old;if(!alive&&previous.svframe$keepInventory())((ServerPlayerEntity)(Object)this).getInventory().clone(old.getInventory());previous.svframe$keepInventory(false);previous.svframe$cancelDrops(false);}
}
