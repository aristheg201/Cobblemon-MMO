package vn.svframe.worldboss.mixin;
import net.minecraft.entity.LivingEntity;import net.minecraft.entity.mob.MobEntity;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;import vn.svframe.worldboss.InfinityRuntime;
@Mixin(MobEntity.class)
public abstract class CosmicTargetMixin {
 @Inject(method="setTarget",at=@At("HEAD"),cancellable=true)
 private void svframe$peace(LivingEntity target,CallbackInfo ci){if(InfinityRuntime.peace(target))ci.cancel();}
}
