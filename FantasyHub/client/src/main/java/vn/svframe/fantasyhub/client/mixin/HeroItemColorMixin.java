package vn.svframe.fantasyhub.client.mixin;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.fantasyhub.client.HeroArmorFeature;
/** Fixed suit icons retain their source colors instead of receiving vanilla leather's brown tint. */
@Mixin(ItemColors.class)
public abstract class HeroItemColorMixin {
 @Inject(method="getColor",at=@At("HEAD"),cancellable=true)
 private void fantasyhub$iconColor(ItemStack stack,int tint,CallbackInfoReturnable<Integer> ci){if(!HeroArmorFeature.suit(stack).isEmpty())ci.setReturnValue(0xffffffff);}
}
