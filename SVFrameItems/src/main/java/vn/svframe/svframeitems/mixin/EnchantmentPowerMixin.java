package vn.svframe.svframeitems.mixin;

import java.util.stream.Stream;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import vn.svframe.svframeitems.runtime.EnchantmentPolicy;

@Mixin(EnchantmentHelper.class)
abstract class EnchantmentPowerMixin {
    @Redirect(method="calculateRequiredExperienceLevel", at=@At(value="INVOKE", target="Lnet/minecraft/item/Item;getEnchantability()I"))
    private static int svframe$cost(Item item, Random random, int slot, int shelves, ItemStack stack) {
        return EnchantmentPolicy.enchantability(stack);
    }
    @Redirect(method="generateEnchantments", at=@At(value="INVOKE", target="Lnet/minecraft/item/Item;getEnchantability()I"))
    private static int svframe$power(Item item, Random random, ItemStack stack, int level, Stream<RegistryEntry<Enchantment>> entries) {
        return EnchantmentPolicy.enchantability(stack);
    }
}
