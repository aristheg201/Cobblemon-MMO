package vn.svframe.fantasyhub.core.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.fantasyhub.core.KabutoRuntime;

/** Also blocks holders created by equipment changes or late tracking during Henshin. */
@Mixin(LivingEntity.class)
abstract class FilamentCosmeticMixin {
  @Inject(method = "filament$addHolder", at = @At("HEAD"), cancellable = true, remap = false)
  private void fantasyhub$cosmetic(LivingEntity entity, Item item, ItemStack stack, String slot, CallbackInfo ci) {
    if (KabutoRuntime.transformed(entity) || vn.svframe.fantasyhub.core.RiderRuntime.transformed(entity)) ci.cancel();
  }
}
