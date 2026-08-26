package vn.svframe.svframelib.version;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.RegistryKey;

public enum VEnchantment {
    POWER(Enchantments.POWER),
    FORTUNE(Enchantments.FORTUNE),
    UNBREAKING(Enchantments.UNBREAKING);

    private final RegistryKey<Enchantment> enchantment;
    VEnchantment(RegistryKey<Enchantment> enchantment) { this.enchantment = enchantment; }
    public RegistryKey<Enchantment> get() { return enchantment; }
}
