package vn.svframe.skript.fabric.mixin;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.skript.fabric.SVFrameSkript;
@Mixin(LivingEntity.class)
public abstract class JumpMixin {
 @Inject(method="jump",at=@At("TAIL"))
 private void svframe$jump(CallbackInfo ci){if((Object)this instanceof ServerPlayerEntity player)SVFrameSkript.dispatch("playerjumpevent",SVFrameSkript.playerEvent(player));}
}
