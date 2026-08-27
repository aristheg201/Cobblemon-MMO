package vn.svframe.svframelib.api.event;

import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.onhit.OnHitEffect;
import vn.svframe.svframelib.fabric.runtime.SVFrameLibEventHub;

import java.util.Objects;
import java.util.function.Consumer;

/** Native Fabric form of the source 1.7.1 cancellable on-hit effect event. */
public class OnHitEffectEvent extends MMOPlayerDataEvent {
    private final OnHitEffect effect;
    private final AttackMetadata attack;
    private boolean cancelled;

    public OnHitEffectEvent(MMOPlayerData data, OnHitEffect effect, AttackMetadata attack) {
        super(Objects.requireNonNull(data, "data"));
        this.effect = Objects.requireNonNull(effect, "effect");
        this.attack = Objects.requireNonNull(attack, "attack");
    }

    public AttackMetadata getAttack() { return attack; }
    public OnHitEffect getEffect() { return effect; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public OnHitEffectEvent call() { SVFrameLibEventHub.events().publish(this); return this; }
    public static AutoCloseable subscribe(Consumer<? super OnHitEffectEvent> listener) {
        return SVFrameLibEventHub.events().subscribe(OnHitEffectEvent.class, listener);
    }
}
