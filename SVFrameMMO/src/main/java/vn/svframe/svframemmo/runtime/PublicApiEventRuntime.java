package vn.svframe.svframemmo.runtime;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerDataLoadEvent;
import vn.svframe.svframemmo.api.event.PlayerLevelChangeEvent;
import vn.svframe.svframemmo.api.event.PlayerLevelUpEvent;

/** Bridges legacy public API events onto the native Fabric lifecycle. */
public final class PublicApiEventRuntime implements ModInitializer {
    @Override
    public void onInitialize() {
        PlayerLevelChangeEvent.EVENT.register(event -> {
            if (event.getReason() == PlayerLevelChangeEvent.Reason.LEVEL_UP)
                new PlayerLevelUpEvent(event.getData(), event.getProfession(), event.getOldLevel(), event.getNewLevel()).call();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var data = SVFrameMMO.playerData().find(handler.player.getUuid());
            if (data != null) new PlayerDataLoadEvent(data).call();
        });
    }
}
