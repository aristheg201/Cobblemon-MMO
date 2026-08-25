package io.lumine.mythic.lib.api.player;

import io.lumine.mythic.lib.player.modifier.ModifierSource;
import io.lumine.mythic.lib.player.modifier.PlayerModifier;
import java.util.Objects;

public enum EquipmentSlot {
    ARMOR(true, false, null),
    HEAD(true, false, net.minecraft.entity.EquipmentSlot.HEAD),
    CHEST(true, false, net.minecraft.entity.EquipmentSlot.CHEST),
    LEGS(true, false, net.minecraft.entity.EquipmentSlot.LEGS),
    FEET(true, false, net.minecraft.entity.EquipmentSlot.FEET),
    ACCESSORY(false, false, null),
    INVENTORY(false, false, null),
    MAIN_HAND(false, true, net.minecraft.entity.EquipmentSlot.MAINHAND),
    OFF_HAND(false, true, net.minecraft.entity.EquipmentSlot.OFFHAND),
    OTHER(false, false, null);

    private final boolean body;
    private final boolean hand;
    private final net.minecraft.entity.EquipmentSlot nativeSlot;

    EquipmentSlot(boolean body, boolean hand, net.minecraft.entity.EquipmentSlot nativeSlot) {
        this.body = body;
        this.hand = hand;
        this.nativeSlot = nativeSlot;
    }

    public boolean isBody() {
        return body;
    }

    public boolean isHand() {
        return hand;
    }

    public net.minecraft.entity.EquipmentSlot toNative() {
        return Objects.requireNonNull(nativeSlot, "No native equipment-slot equivalent");
    }

    private EquipmentSlot getOppositeHand() {
        if (!isHand()) {
            throw new IllegalArgumentException("Not a hand equipment slot");
        }
        return this == MAIN_HAND ? OFF_HAND : MAIN_HAND;
    }

    public boolean isCompatible(PlayerModifier modifier) {
        return isCompatible(modifier.getSource(), modifier.getSlot());
    }

    public boolean isCompatible(ModifierSource source, EquipmentSlot slot) {
        if (!isHand()) {
            throw new IllegalArgumentException("Instance called must be a hand equipment slot");
        }
        if (slot == OTHER) {
            return true;
        }

        return switch (source) {
            case VOID -> false;
            case OTHER -> true;
            case MELEE_WEAPON, RANGED_WEAPON -> slot == this;
            case OFFHAND_ITEM -> slot == OFF_HAND;
            case MAINHAND_ITEM -> slot == MAIN_HAND;
            case HAND_ITEM -> slot.isHand();
            case ARMOR -> slot.body;
            case ACCESSORY -> slot == ACCESSORY;
            case ORNAMENT -> slot == INVENTORY;
        };
    }

    public static EquipmentSlot fromNative(net.minecraft.entity.EquipmentSlot slot) {
        for (EquipmentSlot value : values()) {
            if (value.nativeSlot == slot) {
                return value;
            }
        }
        return OTHER;
    }
}
