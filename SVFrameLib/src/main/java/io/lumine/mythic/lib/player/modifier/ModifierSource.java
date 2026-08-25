package io.lumine.mythic.lib.player.modifier;

public enum ModifierSource {
    MELEE_WEAPON,
    RANGED_WEAPON,
    OFFHAND_ITEM,
    MAINHAND_ITEM,
    HAND_ITEM,
    ARMOR,
    ACCESSORY,
    ORNAMENT,
    OTHER,
    VOID;

    public boolean isWeapon() {
        return this == MELEE_WEAPON || this == RANGED_WEAPON;
    }

    public boolean isHandheld() {
        return isEquipment() && this != ARMOR && this != ACCESSORY;
    }

    public boolean isEquipment() {
        return this != VOID && this != OTHER;
    }
}
