package vn.svframe.svframeitems.mixin;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframeitems.runtime.SourceItemRules;
@Mixin(Ingredient.class)
public abstract class IngredientRulesMixin {
    @Inject(method="test(Lnet/minecraft/item/ItemStack;)Z",at=@At("HEAD"),cancellable=true)
    private void svframe$recipes(ItemStack stack,CallbackInfoReturnable<Boolean> ci){if(stack!=null&&(SourceItemRules.flag(stack,"disable-crafting")||SourceItemRules.flag(stack,"disable-smelting")||SourceItemRules.flag(stack,"disable-smithing")))ci.setReturnValue(false);}
}
