package vn.svframe.svframelib.api.stat.provider;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.PlayerMetadata;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric-native player stat provider API matching SVFrameLib 1.7.1 semantics. */
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
