package vn.svframe.svframeitems.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import vn.svframe.svframeitems.model.*;

import java.util.*;

public final class ItemCodec {
    private static final String ROOT = "SVFrameItems";
    private ItemCodec() {}

    public static boolean isSVFrameItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtComponent custom = stack.get(DataComponentTypes.CUSTOM_DATA);
        return custom != null && custom.copyNbt().contains(ROOT, NbtElement.COMPOUND_TYPE);
    }

    public static void write(ItemStack stack, ItemInstance instance) {
        Objects.requireNonNull(stack, "stack"); Objects.requireNonNull(instance, "instance");
        NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, root -> {
            root.put(ROOT, ItemStateNbtCodec.encode(instance));
            root.putString("SVFRAMEITEMS_ITEM_TYPE", instance.typeId());
            root.putString("SVFRAMEITEMS_ITEM_ID", instance.definitionId());
            root.putInt("SVFRAMEITEMS_ITEM_LEVEL", instance.itemLevel());
            root.putInt("SVFRAMEITEMS_UPGRADE_LEVEL", instance.upgradeLevel());
        });
    }

    public static Optional<ItemInstance> read(ItemStack stack) {
        if (!isSVFrameItem(stack)) return Optional.empty();
        NbtCompound root = Objects.requireNonNull(stack.get(DataComponentTypes.CUSTOM_DATA)).copyNbt();
        return ItemStateNbtCodec.decode(root.getCompound(ROOT));
    }

    public static Optional<String> metadata(ItemStack stack, String key) {
        String normalized = ItemInstance.normalizeMetadataKey(key);
        return read(stack).map(ItemInstance::metadata).map(value -> value.get(normalized));
    }

    public static boolean setMetadata(ItemStack stack, String key, String value) {
        Optional<ItemInstance> current = read(stack);
        if (current.isEmpty()) return false;
        write(stack, current.get().withMetadata(key, value));
        return true;
    }
}
