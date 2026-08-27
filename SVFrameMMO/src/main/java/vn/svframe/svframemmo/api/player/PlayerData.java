package vn.svframe.svframemmo.api.player;

import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframelib.fabric.runtime.RpgProfileRegistry;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerResourceUpdateEvent;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import java.util.*;

public final class PlayerData {
    private final UUID id; private transient ServerPlayerEntity player; private String playerClass="HUMAN"; private int level,classPoints,skillPoints,attributePoints; private double experience,mana,stamina,stellium; private long combatUntilTick; private final Map<String,Double> attributes=new LinkedHashMap<>();
    PlayerData(UUID id){this.id=id; var c=SVFrameMMO.config(); level=c.defaultLevel();classPoints=c.defaultClassPoints();skillPoints=c.defaultSkillPoints();attributePoints=c.defaultAttributePoints();mana=c.defaultMana();stamina=c.defaultStamina();stellium=c.defaultStellium();}
    public UUID getUniqueId(){return id;} public ServerPlayerEntity getPlayer(){return player;} public MMOPlayerData getMMOPlayerData(){return player==null?MMOPlayerData.get(id):MMOPlayerData.setup(player);} public void attach(ServerPlayerEntity p){player=p; MMOPlayerData.setup(p); clampAll();} public void detach(){player=null;}
    public String getClassId(){return playerClass;} public void setClassId(String v){playerClass=v==null?"HUMAN":v.trim().toUpperCase(Locale.ROOT).replace('-','_');}
    public int getLevel(){return level;} public void setLevel(int v){level=Math.max(1,v);} public double getExperience(){return experience;} public void setExperience(double v){experience=Math.max(0,v);} public int getClassPoints(){return classPoints;} public int getSkillPoints(){return skillPoints;} public int getAttributePoints(){return attributePoints;} public Map<String,Double> getAttributes(){return Collections.unmodifiableMap(attributes);} public void setAttribute(String id,double value){attributes.put(id.toUpperCase(Locale.ROOT),value);}
    public boolean isInCombat(){return SVFrameMMO.currentTick()<combatUntilTick;} public void markCombat(){combatUntilTick=SVFrameMMO.currentTick()+SVFrameMMO.config().combatTimerSeconds()*20L;}
    public double getMana(){return mana;} public double getStamina(){return stamina;} public double getStellium(){return stellium;} public boolean setMana(double a,ResourceUpdateReason r){return setResource(PlayerResource.MANA,a,r);} public boolean giveMana(double a,ResourceUpdateReason r){return setMana(mana+a,r);} public boolean setStamina(double a,ResourceUpdateReason r){return setResource(PlayerResource.STAMINA,a,r);} public boolean giveStamina(double a,ResourceUpdateReason r){return setStamina(stamina+a,r);} public boolean setStellium(double a,ResourceUpdateReason r){return setResource(PlayerResource.STELLIUM,a,r);} public boolean giveStellium(double a,ResourceUpdateReason r){return setStellium(stellium+a,r);}
    public double getResource(PlayerResource r){return switch(r){case HEALTH->player==null?0:player.getHealth();case MANA->mana;case STAMINA->stamina;case STELLIUM->stellium;};}
    public double getMaxResource(PlayerResource r){if(r==PlayerResource.HEALTH)return player==null?20:player.getMaxHealth(); double v=getMMOPlayerData().getStatMap().getStat(r.getMaxStat()); return Math.max(0,v);}
    public boolean setResource(PlayerResource r,double amount,ResourceUpdateReason reason){double max=getMaxResource(r), old=getResource(r), next=Math.max(0,Math.min(amount,max));if(Double.compare(old,next)==0)return false;if(reason!=ResourceUpdateReason.CHOOSE_CLASS){var event=new PlayerResourceUpdateEvent(this,r,old,next,reason).call();if(event.isCancelled())return false;next=Math.max(0,Math.min(event.getNewAmount(),max));}switch(r){case HEALTH->{if(player==null)return false;player.setHealth((float)next);}case MANA->mana=next;case STAMINA->stamina=next;case STELLIUM->stellium=next;}return true;}
    public void clampAll(){for(PlayerResource r:PlayerResource.values())if(r!=PlayerResource.HEALTH)setResource(r,getResource(r),ResourceUpdateReason.CLAMPING);}
    public RpgProfileRegistry.Snapshot snapshot(){return new RpgProfileRegistry.Snapshot(level,playerClass,attributes);}
    public static PlayerData blank(UUID id){return new PlayerData(id);} public void restore(String clazz,int level,double exp,int cp,int sp,int ap,double mana,double stamina,double stellium,Map<String,Double> attrs){this.playerClass=clazz;this.level=Math.max(1,level);this.experience=Math.max(0,exp);this.classPoints=cp;this.skillPoints=sp;this.attributePoints=ap;this.mana=mana;this.stamina=stamina;this.stellium=stellium;this.attributes.clear();if(attrs!=null)this.attributes.putAll(attrs);}
}
