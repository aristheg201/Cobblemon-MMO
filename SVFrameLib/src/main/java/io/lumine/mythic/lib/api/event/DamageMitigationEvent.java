package io.lumine.mythic.lib.api.event;
import io.lumine.mythic.lib.api.player.MMOPlayerData; import io.lumine.mythic.lib.damage.mitigation.MitigationType; import net.minecraft.entity.damage.DamageSource; import vn.svframe.mythiclibfabric.runtime.MythicLibEventHub;
public class DamageMitigationEvent extends MMOPlayerDataEvent {
 private final MitigationType type; private final DamageSource source; private boolean cancelled;
 public DamageMitigationEvent(MMOPlayerData data,MitigationType type,DamageSource source){super(data);this.type=type;this.source=source;} public DamageSource getDamageSource(){return source;} public MitigationType getType(){return type;} public boolean isCancelled(){return cancelled;} public void setCancelled(boolean v){cancelled=v;} public void call(){MythicLibEventHub.events().publish(this);}
}
