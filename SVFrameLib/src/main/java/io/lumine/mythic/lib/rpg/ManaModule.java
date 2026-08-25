package io.lumine.mythic.lib.rpg;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import io.lumine.mythic.lib.rpg.provided.NativeManaModule;
public interface ManaModule {
    double getMana(MMOPlayerData data);double getStamina(MMOPlayerData data);boolean setMana(MMOPlayerData data,double value,ResourceUpdateReason reason);boolean setStamina(MMOPlayerData data,double value,ResourceUpdateReason reason);
    static ManaModule from(String name){return new NativeManaModule();}
}
