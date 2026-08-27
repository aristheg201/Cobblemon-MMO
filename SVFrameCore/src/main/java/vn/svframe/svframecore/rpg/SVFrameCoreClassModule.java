package vn.svframe.svframecore.rpg;

import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.rpg.ClassModule;

public final class SVFrameCoreClassModule implements ClassModule {
    @Override public String getClass(MMOPlayerData data) { return PlayerData.get(data).getPlayerClass(); }
}
