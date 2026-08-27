package vn.svframe.svframemmo.api.player.profess.resource;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.api.player.PlayerData;
public enum PlayerResource {
    HEALTH("HEALTH_REGENERATION","MAX_HEALTH_REGENERATION","MAX_HEALTH"), MANA("MANA_REGENERATION","MAX_MANA_REGENERATION","MAX_MANA"), STAMINA("STAMINA_REGENERATION","MAX_STAMINA_REGENERATION","MAX_STAMINA"), STELLIUM("STELLIUM_REGENERATION","MAX_STELLIUM_REGENERATION","MAX_STELLIUM");
    private final String regenStat,maxRegenStat,maxStat; PlayerResource(String r,String mr,String m){regenStat=r;maxRegenStat=mr;maxStat=m;}
    public String getRegenStat(){return regenStat;} public String getMaxRegenStat(){return maxRegenStat;} public String getMaxStat(){return maxStat;}
    public double getCurrent(PlayerData d){return d.getResource(this);} public double getMax(PlayerData d){return d.getMaxResource(this);} public boolean setCurrent(PlayerData d,double a,ResourceUpdateReason r){return d.setResource(this,a,r);} public boolean give(PlayerData d,double a,ResourceUpdateReason r){return d.setResource(this,getCurrent(d)+a,r);} public boolean regen(PlayerData d,double a){return give(d,a,ResourceUpdateReason.REGENERATION);}
}
