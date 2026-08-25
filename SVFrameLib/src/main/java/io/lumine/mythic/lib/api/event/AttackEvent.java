package io.lumine.mythic.lib.api.event;

import io.lumine.mythic.lib.damage.AttackMetadata;
import io.lumine.mythic.lib.damage.DamageMetadata;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;

/**
 * Fabric-native cancellable attack event. Damage processing must dispatch this event
 * before committing the attack and honor {@link #isCancelled()}.
 */
public class AttackEvent {
    @FunctionalInterface
    public interface Listener {
        void onAttack(AttackEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onAttack(event);
            });

    private final AttackMetadata attack;
    private final LivingEntity entity;
    private boolean cancelled;

    public AttackEvent(AttackMetadata attack) {
        this.attack = attack;
        this.entity = attack.getTarget();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public AttackMetadata getAttack() {
        return attack;
    }

    public DamageMetadata getDamage() {
        return attack.getDamage();
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public AttackEvent call() {
        EVENT.invoker().onAttack(this);
        return this;
    }
}
