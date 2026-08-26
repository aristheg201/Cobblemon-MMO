package vn.svframe.svframelib.api.event;
import vn.svframe.svframelib.api.player.MMOPlayerData; import vn.svframe.svframelib.damage.onhit.OnHitEffect; import net.minecraft.entity.damage.DamageSource; import vn.svframe.mythiclibfabric.runtime.MythicLibEventHub;
public class OnHitEffectEvent extends MMOPlayerDataEvent {
 private final OnHitEffect effect; private final DamageSource source; private boolean cancelled;
 public OnHitEffectEvent(MMOPlayerData data,OnHitEffect effect,DamageSource source){super(data);this.effect=effect;this.source=source;} public DamageSource getDamageSource(){return source;} public OnHitEffect getEffect(){return effect;} public boolean isCancelled(){return cancelled;} public void setCancelled(boolean v){cancelled=v;} public void call(){MythicLibEventHub.events().publish(this);}
}
