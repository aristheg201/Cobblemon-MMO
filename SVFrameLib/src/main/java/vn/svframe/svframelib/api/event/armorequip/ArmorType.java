package vn.svframe.svframelib.api.event.armorequip;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/** Fabric-native armor classifier preserving SVFrameLib 1.7.1 matching rules and slot ids. */
public enum ArmorType {
    HELMET(5),
    CHESTPLATE(6),
    LEGGINGS(7),
    BOOTS(8);

    private final int slot;

    ArmorType(int slot) {
        this.slot = slot;
    }

    public static ArmorType matchType(ItemStack item) {
        if (item == null || item.isEmpty()) return null;

        String name = Registries.ITEM.getId(item.getItem()).getPath().toUpperCase(java.util.Locale.ROOT);
        if (name.endsWith("_HELMET") || name.endsWith("_SKULL") || name.endsWith("_HEAD")
                || name.equals("CARVED_PUMPKIN")) {
            return HELMET;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return CHESTPLATE;
        if (name.endsWith("_LEGGINGS")) return LEGGINGS;
        if (name.endsWith("_BOOTS")) return BOOTS;
        return null;
    }

    public int getSlot() {
        return slot;
    }
}
