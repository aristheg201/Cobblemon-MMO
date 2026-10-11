package vn.svframe.svframeitems.mixin;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframeitems.runtime.RetainedItems;
@Mixin(ServerPlayerEntity.class)
public abstract class ServerItemRetentionMixin {
    @Inject(method="copyFrom",at=@At("RETURN"))
    private void svframe$respawn(ServerPlayerEntity oldPlayer,boolean alive,CallbackInfo ci){var self=(ServerPlayerEntity)(Object)this;var before=((RetainedItems)oldPlayer).svframe$retained();var after=((RetainedItems)self).svframe$retained();after.putAll(before);before.clear();vn.svframe.svframeitems.runtime.RetainedInventory.restore(self);}
}
