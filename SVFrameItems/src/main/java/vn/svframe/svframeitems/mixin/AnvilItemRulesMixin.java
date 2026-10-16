package vn.svframe.svframeitems.mixin;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframeitems.runtime.SourceItemRules;
import net.minecraft.item.ItemStack;
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilItemRulesMixin {
    @Inject(method="updateResult",at=@At("RETURN"))
    private void svframe$anvil(CallbackInfo ci){
        var self=(AnvilScreenHandler)(Object)this;var left=self.getSlot(0).getStack();var right=self.getSlot(1).getStack();var result=self.getSlot(2).getStack();
        boolean repair=!result.isEmpty()&&result.getDamage()<left.getDamage();
        boolean enchant=!net.minecraft.enchantment.EnchantmentHelper.getEnchantments(right).isEmpty();
        if((repair&&SourceItemRules.flag(left,"disable-repairing"))||(enchant&&SourceItemRules.flag(left,"disable-enchanting")))self.getSlot(2).setStack(ItemStack.EMPTY);
    }
}
