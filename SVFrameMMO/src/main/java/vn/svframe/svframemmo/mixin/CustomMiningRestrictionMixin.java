package vn.svframe.svframemmo.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vn.svframe.svframemmo.manager.RestrictionManager;
import vn.svframe.svframemmo.profession.mining.CustomMiningRuntime;

/** Replaces vanilla harvest-tier checks with the configured custom-mining permission tree. */
@Mixin(CustomMiningRuntime.class)
public abstract class CustomMiningRestrictionMixin {
    @Redirect(method = "beforeBreak", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isToolRequired()Z"))
    private boolean svframemmo$alwaysCheckConfiguredRestriction(BlockState state) {
        return true;
    }

    @Redirect(method = "beforeBreak", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isSuitableFor(Lnet/minecraft/block/BlockState;)Z"))
    private boolean svframemmo$checkConfiguredRestriction(ItemStack stack, BlockState state) {
        return RestrictionManager.instance().checkPermissions(stack, state);
    }
}
