package vn.svframe.svframecore.rpg;

import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.rpg.LevelModule;

public final class SVFrameCoreLevelModule implements LevelModule {
    @Override public int getLevel(MMOPlayerData data) { return PlayerData.get(data).getLevel(); }
}
