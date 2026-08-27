package vn.svframe.svframelib.rpg.provided;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.rpg.ManaModule;
public class NativeManaModule implements ManaModule {
    private PlayerResourceData data(MMOPlayerData player){PlayerResourceData data=player.getExternalData("svframelib:resources",PlayerResourceData.class);if(data==null){data=new PlayerResourceData(player);player.setExternalData("svframelib:resources",data);data.openSession();}return data;}
    @Override public boolean setMana(MMOPlayerData data,double value,ResourceUpdateReason reason){return data(data).setMana(value,reason);}
    @Override public boolean setStamina(MMOPlayerData data,double value,ResourceUpdateReason reason){return data(data).setStamina(value,reason);}
    @Override public double getMana(MMOPlayerData data){return data(data).getMana();}
    @Override public double getStamina(MMOPlayerData data){return data(data).getStamina();}
}
