package vn.svframe.svframeitems.runtime;

import net.minecraft.item.*;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.ItemCodec;

/** Resolve a vanilla enchantment category without changing an authored item's material or model. */
public final class EnchantmentPolicy {
    private static boolean overrideImportedDisableFlags=true;
    private EnchantmentPolicy() {}
    public static void configure(java.util.Map<String,Object> config) {
        overrideImportedDisableFlags=!Boolean.FALSE.equals(vn.svframe.svframelib.config.YamlLite.map(config.get("enchanting")).get("override-imported-disable-flags"));
    }
    public static boolean overridesLegacyDisable(ItemStack stack) {
        return overrideImportedDisableFlags && carrier(stack)!=null;
    }
    public static Item carrier(ItemStack stack) {
        var instance = ItemCodec.read(stack);
        if (instance.isEmpty()) return null;
        var definition = SVFrameItems.registry().item(instance.get().definitionId());
        if (definition == null) return null;
        if (stack.getItem() instanceof ArmorItem armor) return switch (armor.getSlotType()) {
            case HEAD -> Items.LEATHER_HELMET;
            case CHEST -> Items.LEATHER_CHESTPLATE;
            case LEGS -> Items.LEATHER_LEGGINGS;
            case FEET -> Items.LEATHER_BOOTS;
            default -> null;
        };
        return switch (definition.typeId()) {
            case "sword", "dagger", "greatsword", "longsword", "katana", "rapier", "staff", "wand", "hammer", "spear" -> Items.IRON_SWORD;
            case "axe", "greataxe" -> Items.IRON_AXE;
            case "bow" -> Items.BOW;
            case "crossbow", "musket", "gun" -> Items.CROSSBOW;
            case "trident" -> Items.TRIDENT;
            case "shield" -> Items.SHIELD;
            case "pickaxe" -> Items.IRON_PICKAXE;
            case "shovel" -> Items.IRON_SHOVEL;
            case "hoe" -> Items.IRON_HOE;
            case "fishing_rod" -> Items.FISHING_ROD;
            default -> null;
        };
    }
    public static int enchantability(ItemStack stack) {
        if (SourceItemRules.flag(stack, "disable-enchanting")) return 0;
        Item carrier = carrier(stack);
        return carrier == null ? stack.getItem().getEnchantability()
                : Math.max(stack.getItem().getEnchantability(), carrier.getEnchantability());
    }
}
