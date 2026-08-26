package vn.svframe.svframelib.api.stat.modifier;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.modifier.ModifierSource;
import vn.svframe.svframelib.player.modifier.ModifierType;
import vn.svframe.svframelib.util.Closeable;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;
import vn.svframe.mythiclibfabric.MythicLibStatMod;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import java.util.UUID;

public class TemporaryStatModifier extends StatModifier implements Closeable {
    private volatile MMOPlayerData data; private volatile long duration; private volatile long startTime; private volatile boolean active;
    public TemporaryStatModifier(String key,String stat,double value,ModifierType type,EquipmentSlot slot,ModifierSource source){super(key,stat,value,type,slot,source);}
    public TemporaryStatModifier(UUID id,String key,String stat,double value,ModifierType type,EquipmentSlot slot,ModifierSource source){super(id,key,stat,value,type,slot,source);}
    public long getDuration(){ensureActive();return duration;}public long getStartTime(){ensureActive();return startTime;}
    public synchronized void register(MMOPlayerData data,long delayTicks){if(active)throw new IllegalStateException("Modifier is already active");if(delayTicks<0)throw new IllegalArgumentException("delayTicks must be >= 0");this.data=data;this.duration=delayTicks;this.startTime=System.currentTimeMillis();long tick=MythicLibFabricMod.currentTick();long expires=delayTicks>Long.MAX_VALUE-tick?Long.MAX_VALUE:tick+delayTicks;NativeStatEngine.Modifier n=toNative();MythicLibStatMod.engine().register(data.getUniqueId(),getStat(),new NativeStatEngine.Modifier(n.id(),n.key(),n.value(),n.type(),n.slot(),n.source(),expires));active=true;}
    @Override public void register(MMOPlayerData data){throw new UnsupportedOperationException("Use #register(MMOPlayerData,long)");}
    @Override public synchronized void close(){ensureActive();unregister(data);active=false;data=null;}public boolean isActive(){return active;}private void ensureActive(){if(!active)throw new IllegalStateException("Modifier is not active");}
}
