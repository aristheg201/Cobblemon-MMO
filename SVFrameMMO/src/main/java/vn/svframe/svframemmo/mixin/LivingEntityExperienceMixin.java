package vn.svframe.svframemmo.mixin;

import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vn.svframe.svframemmo.SVFrameMMO;

@Mixin(LivingEntity.class)
public abstract class LivingEntityExperienceMixin {
    @Inject(method = "eatFood", at = @At("HEAD"))
    private void svframemmo$eat(World world, ItemStack stack, FoodComponent food, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object) this instanceof ServerPlayerEntity player && !world.isClient())
            SVFrameMMO.nativeExperience().onEaten(player, stack.copyWithCount(1));
    }
}
