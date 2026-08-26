package vn.svframe.svframelib.api.stat.provider;

import vn.svframe.svframelib.api.item.NBTItem;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/** Fabric-native equipment stat provider for non-player living entities. */
public class EntityStatProvider implements StatProvider {
    private final Set<NBTItem> equipment = new HashSet<>();
    private final LivingEntity entity;

    public EntityStatProvider(LivingEntity entity) {
        this.entity = entity;
        for (ItemStack armor : entity.getArmorItems()) registerItem(armor);
        registerItem(entity.getMainHandStack());
        registerItem(entity.getOffHandStack());
    }

    @Override
    public EquipmentSlot getActionHand() {
        return EquipmentSlot.MAIN_HAND;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    private void registerItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getComponentChanges().isEmpty()) return;
        equipment.add(NBTItem.get(stack));
    }

    @Override
    public double getStat(String id) {
        double total = 0d;
        for (NBTItem item : equipment) total += item.getStat(id);
        return total;
    }
}
