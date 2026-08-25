package io.lumine.mythic.lib.api.event;
import io.lumine.mythic.lib.api.player.MMOPlayerData; import io.lumine.mythic.lib.damage.onhit.OnHitEffect; import net.minecraft.entity.damage.DamageSource; import vn.svframe.mythiclibfabric.runtime.MythicLibEventHub;
public class OnHitEffectEvent extends MMOPlayerDataEvent {
 private final OnHitEffect effect; private final DamageSource source; private boolean cancelled;
 public OnHitEffectEvent(MMOPlayerData data,OnHitEffect effect,DamageSource source){super(data);this.effect=effect;this.source=source;} public DamageSource getDamageSource(){return source;} public OnHitEffect getEffect(){return effect;} public boolean isCancelled(){return cancelled;} public void setCancelled(boolean v){cancelled=v;} public void call(){MythicLibEventHub.events().publish(this);}
}
