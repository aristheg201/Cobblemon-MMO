package vn.svframe.svframelib.data;
import vn.svframe.svframelib.api.player.MMOPlayerData; import vn.svframe.svframelib.module.MMOPlugin; import vn.svframe.svframelib.profile.SessionUpdateReason;
import net.minecraft.server.network.ServerPlayerEntity; import java.util.*;
public abstract class SynchronizedDataHolder implements OfflineDataHolder {
    private final MMOPlayerData playerData; private final MMOPlugin mmoPlugin; private volatile boolean ready; private final Object sessionLock=new Object();
    public SynchronizedDataHolder(MMOPlugin plugin,MMOPlayerData playerData){this.mmoPlugin=Objects.requireNonNull(plugin);this.playerData=Objects.requireNonNull(playerData);}
    public MMOPlayerData getMMOPlayerData(){return playerData;} public UUID getUniqueId(){return playerData.getUniqueId();}
    public UUID getProfileId(){return playerData.hasProfileSession()?playerData.getProfileId():getUniqueId();}
    public UUID getOfficialId(){return playerData.getOfficialId();} public ServerPlayerEntity getPlayer(){return playerData.getPlayer();}
    public UUID getEffectiveId(){return mmoPlugin.isProfilePlugin()&&playerData.hasProfileSession()?getProfileId():getOfficialId();}
    public MMOPlugin getOwningPlugin(){return mmoPlugin;}
    public void onSaved(SessionUpdateReason reason){ if(reason!=SessionUpdateReason.AUTOSAVE && reason!=SessionUpdateReason.UNSPECIFIED) markSessionClosed(); }
    protected void onSessionReady(){} protected void onSessionClosed(){}
    public boolean isSessionReady(){return ready;}
    public void markSessionReady(){synchronized(sessionLock){if(ready)return;ready=true;onSessionReady();}}
    public void markSessionClosed(){synchronized(sessionLock){if(!ready)return;ready=false;onSessionClosed();}}
    public boolean isSynchronized(){return ready;}
    public boolean shouldBeSaved(){return ready && (!playerData.isLookup() || playerData.hasProfileSession());}
}
