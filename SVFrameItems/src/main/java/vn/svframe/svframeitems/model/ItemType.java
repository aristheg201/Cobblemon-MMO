package vn.svframe.svframeitems.model;

import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import java.util.*;

public record ItemType(String id, NativeStatEngine.ModifierSource modifierSource, Set<NativeStatEngine.EquipmentSlot> allowedSlots, int maxStackSize) {
    public ItemType {
        id = normalize(id);
        Objects.requireNonNull(modifierSource, "modifierSource");
        allowedSlots = allowedSlots == null ? Set.of() : Set.copyOf(allowedSlots);
        if (maxStackSize < 1 || maxStackSize > 99) throw new IllegalArgumentException("maxStackSize out of range");
    }
    public boolean canEquip(NativeStatEngine.EquipmentSlot slot) { return allowedSlots.contains(slot); }
    public static String normalize(String value) { return Objects.requireNonNull(value, "id").trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_'); }
}
