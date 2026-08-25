package io.lumine.mythic.lib.api.stat.provider;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.PlayerMetadata;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric-native player stat provider API matching MythicLib 1.7.1 semantics. */
public interface PlayerStatProvider extends StatProvider {
    default ServerPlayerEntity getPlayer() {
        return getData().getPlayer();
    }

    PlayerMetadata cache(EquipmentSlot slot);

    MMOPlayerData getData();

    @Override
    default LivingEntity getEntity() {
        return getData().getPlayer();
    }
}
