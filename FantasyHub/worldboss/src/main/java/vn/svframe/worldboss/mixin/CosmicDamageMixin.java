package vn.svframe.worldboss.mixin;
import net.minecraft.entity.LivingEntity;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import vn.svframe.worldboss.InfinityRuntime;
@Mixin(LivingEntity.class)
public abstract class CosmicDamageMixin {
 @ModifyVariable(method="damage",at=@At("HEAD"),argsOnly=true)
 private float svframe$mitigation(float amount){return (float)(amount*InfinityRuntime.damageScale((LivingEntity)(Object)this));}
}
