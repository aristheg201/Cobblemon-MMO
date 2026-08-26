package vn.svframe.svframelib.api.stat.provider;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric-native stat-provider contract used by combat and skills. */
public interface StatProvider {
    double getStat(String id);

    LivingEntity getEntity();

    EquipmentSlot getActionHand();

    static StatProvider get(LivingEntity entity) {
        return get(entity, EquipmentSlot.MAIN_HAND, true);
    }

    static StatProvider generate(LivingEntity entity, EquipmentSlot actionHand) {
        return get(entity, actionHand, true);
    }

    static StatProvider get(LivingEntity entity, EquipmentSlot actionHand, boolean cached) {
        if (!(entity instanceof ServerPlayerEntity player)) {
            return new EntityStatProvider(entity);
        }

        var statMap = MMOPlayerData.get(player.getUuid()).getStatMap();
        return cached ? statMap.cache(actionHand) : statMap;
    }
}
