package io.lumine.mythic.lib.rpg.provided;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.player.resource.ResourceUpdateReason;
import io.lumine.mythic.lib.rpg.ManaModule;
public class NativeManaModule implements ManaModule {
    private PlayerResourceData data(MMOPlayerData player){PlayerResourceData data=player.getExternalData("mythiclib:resources",PlayerResourceData.class);if(data==null){data=new PlayerResourceData(player);player.setExternalData("mythiclib:resources",data);data.openSession();}return data;}
    @Override public boolean setMana(MMOPlayerData data,double value,ResourceUpdateReason reason){return data(data).setMana(value,reason);}
    @Override public boolean setStamina(MMOPlayerData data,double value,ResourceUpdateReason reason){return data(data).setStamina(value,reason);}
    @Override public double getMana(MMOPlayerData data){return data(data).getMana();}
    @Override public double getStamina(MMOPlayerData data){return data(data).getStamina();}
}
