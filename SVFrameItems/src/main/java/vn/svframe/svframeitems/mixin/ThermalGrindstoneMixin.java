package vn.svframe.svframeitems.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.GrindstoneScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframeitems.runtime.ThermalTuningRuntime;

@Mixin(GrindstoneScreenHandler.class)
abstract class ThermalGrindstoneMixin {
    @Inject(method="grind",at=@At("RETURN"))
    private void svframe$intrinsic(ItemStack input,CallbackInfoReturnable<ItemStack> ci) {
        if(ThermalTuningRuntime.intrinsic(input)&&!ci.getReturnValue().isEmpty())ThermalTuningRuntime.apply(ci.getReturnValue());
    }
}
