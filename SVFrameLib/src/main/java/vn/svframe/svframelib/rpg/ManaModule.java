package vn.svframe.svframelib.rpg;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.rpg.provided.NativeManaModule;
public interface ManaModule {
    double getMana(MMOPlayerData data);double getStamina(MMOPlayerData data);boolean setMana(MMOPlayerData data,double value,ResourceUpdateReason reason);boolean setStamina(MMOPlayerData data,double value,ResourceUpdateReason reason);
    static ManaModule from(String name){return new NativeManaModule();}
}
