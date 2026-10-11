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
    private void svframe$anvil(CallbackInfo ci){var self=(AnvilScreenHandler)(Object)this;if(SourceItemRules.flag(self.getSlot(0).getStack(),"disable-repairing")||SourceItemRules.flag(self.getSlot(0).getStack(),"disable-enchanting"))self.getSlot(2).setStack(ItemStack.EMPTY);}
}
