package vn.svframe.svframeitems.mixin;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframeitems.runtime.*;

@Mixin(Enchantment.class)
abstract class EnchantmentCategoryMixin {
    @Inject(method={"isSupportedItem","isAcceptableItem"}, at=@At("RETURN"), cancellable=true)
    private void svframe$supported(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        var carrier = EnchantmentPolicy.carrier(stack);
        if (ThermalTuningRuntime.isThermal((Enchantment)(Object)this) && ThermalTuningRuntime.equipment(stack)
                && !SourceItemRules.flag(stack,"disable-enchanting")) {ci.setReturnValue(true);return;}
        if (carrier != null && !ci.getReturnValueZ() && !SourceItemRules.flag(stack,"disable-enchanting"))
            ci.setReturnValue(((Enchantment)(Object)this).isSupportedItem(new ItemStack(carrier)));
    }
    @Inject(method="isPrimaryItem", at=@At("RETURN"), cancellable=true)
    private void svframe$primary(ItemStack stack, CallbackInfoReturnable<Boolean> ci) {
        if (ThermalTuningRuntime.isThermal((Enchantment)(Object)this) && ThermalTuningRuntime.onlyThermalTuning(stack)) {
            ci.setReturnValue(false);return;
        }
        var carrier = EnchantmentPolicy.carrier(stack);
        if (carrier != null && !ci.getReturnValueZ() && !SourceItemRules.flag(stack,"disable-enchanting"))
            ci.setReturnValue(((Enchantment)(Object)this).isPrimaryItem(new ItemStack(carrier)));
    }
}
