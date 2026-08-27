package vn.svframe.svframeitems.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import vn.svframe.svframeitems.model.*;

public final class ItemFormatter {
    public void apply(ItemStack stack, ItemDefinition definition, ItemInstance instance, ItemRarity rarity) {
        StringBuilder name = new StringBuilder(definition.displayName());
        if (instance.upgradeLevel() > 0) name.append(" +").append(instance.upgradeLevel());
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name.toString()));
    }
}
