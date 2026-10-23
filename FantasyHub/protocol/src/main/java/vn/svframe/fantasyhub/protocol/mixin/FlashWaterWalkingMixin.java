package vn.svframe.fantasyhub.protocol.mixin;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.fantasyhub.protocol.FlashMovement;
@Mixin(LivingEntity.class)
abstract class FlashWaterWalkingMixin {
 @Inject(method="canWalkOnFluid",at=@At("HEAD"),cancellable=true)
 private void fantasyhub$waterSurface(FluidState fluid,CallbackInfoReturnable<Boolean> cir){if((Object)this instanceof PlayerEntity p&&fluid.isIn(FluidTags.WATER)&&FlashMovement.waterWalking(p))cir.setReturnValue(true);}
}
