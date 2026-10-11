package vn.svframe.svframeitems.mixin;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframeitems.runtime.SourceItemRules;
@Mixin(ItemStack.class)
public abstract class ItemRulesMixin {
    @Inject(method="isEnchantable",at=@At("HEAD"),cancellable=true)
    private void svframe$enchanting(CallbackInfoReturnable<Boolean> ci){if(SourceItemRules.flag((ItemStack)(Object)this,"disable-enchanting"))ci.setReturnValue(false);}
    @Inject(method="applyAttributeModifiers",at=@At("HEAD"),cancellable=true)
    private void svframe$canonicalAttributes(net.minecraft.entity.EquipmentSlot slot,java.util.function.BiConsumer<net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>,net.minecraft.entity.attribute.EntityAttributeModifier> consumer,CallbackInfo ci){
        if(SourceItemRules.ownsNativeAttributes((ItemStack)(Object)this))ci.cancel();
    }
}
