package vn.svframe.svframelib.api.event;
import vn.svframe.svframelib.api.player.MMOPlayerData; import vn.svframe.svframelib.damage.mitigation.MitigationType; import net.minecraft.entity.damage.DamageSource; import vn.svframe.mythiclibfabric.runtime.MythicLibEventHub;
public class DamageMitigationEvent extends MMOPlayerDataEvent {
 private final MitigationType type; private final DamageSource source; private boolean cancelled;
 public DamageMitigationEvent(MMOPlayerData data,MitigationType type,DamageSource source){super(data);this.type=type;this.source=source;} public DamageSource getDamageSource(){return source;} public MitigationType getType(){return type;} public boolean isCancelled(){return cancelled;} public void setCancelled(boolean v){cancelled=v;} public void call(){MythicLibEventHub.events().publish(this);}
}
