package vn.svframe.svframeitems.mixin;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.EnchantmentTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframeitems.runtime.ThermalTuningRuntime;

/** Intrinsic, automatically restored enchantments must never become a repeatable XP source. */
@Mixin(targets="net.minecraft.screen.GrindstoneScreenHandler$4")
abstract class ThermalGrindstoneXpMixin {
    @Inject(method="getExperience(Lnet/minecraft/item/ItemStack;)I",at=@At("HEAD"),cancellable=true)
    private void svframe$experience(ItemStack stack,CallbackInfoReturnable<Integer> ci) {
        if(!ThermalTuningRuntime.intrinsic(stack))return;
        int xp=0;
        for(var enchant:EnchantmentHelper.getEnchantments(stack).getEnchantmentEntries()) {
            var entry=enchant.getKey();
            if(!entry.isIn(EnchantmentTags.CURSE)&&!entry.getKey().map(k->k.getValue().equals(ThermalTuningRuntime.ID)).orElse(false))
                xp+=entry.value().getMinPower(enchant.getIntValue());
        }
        ci.setReturnValue(xp);
    }
}
