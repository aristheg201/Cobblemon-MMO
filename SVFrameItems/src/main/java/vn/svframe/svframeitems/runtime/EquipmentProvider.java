package vn.svframe.svframeitems.runtime;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import java.util.*;

@FunctionalInterface
public interface EquipmentProvider {
    Collection<EquippedItem> equipment(ServerPlayerEntity player);
    record EquippedItem(String key, ItemStack stack, NativeStatEngine.EquipmentSlot slot) {
        public EquippedItem { key=Objects.requireNonNull(key); stack=Objects.requireNonNull(stack); slot=Objects.requireNonNull(slot); }
    }
}
