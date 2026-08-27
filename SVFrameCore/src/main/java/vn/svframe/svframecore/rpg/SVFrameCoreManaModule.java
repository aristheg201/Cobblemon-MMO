package vn.svframe.svframecore.rpg;

import vn.svframe.svframecore.api.player.PlayerData;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.rpg.ManaModule;

public final class SVFrameCoreManaModule implements ManaModule {
    @Override public double getMana(MMOPlayerData data) { return PlayerData.get(data).getMana(); }
    @Override public double getStamina(MMOPlayerData data) { return PlayerData.get(data).getStamina(); }
    @Override public boolean setMana(MMOPlayerData data, double value, ResourceUpdateReason reason) { return PlayerData.get(data).setMana(value, reason); }
    @Override public boolean setStamina(MMOPlayerData data, double value, ResourceUpdateReason reason) { return PlayerData.get(data).setStamina(value, reason); }
}
