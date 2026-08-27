package vn.svframe.svframelib.api.event;

import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.player.PlayerMetadata;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;

/** Fabric-native player kill event preserving SVFrameLib 1.7.1 payload semantics. */
public class PlayerKillEntityEvent extends MMOPlayerDataEvent {
    @FunctionalInterface
    public interface Listener {
        void onPlayerKillEntity(PlayerKillEntityEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onPlayerKillEntity(event);
            });

    private final LivingEntity target;
    private final AttackMetadata attack;

    public PlayerKillEntityEvent(AttackMetadata attack, LivingEntity target) {
        super(((PlayerMetadata) attack.getAttacker()).getData());
        this.attack = attack;
        this.target = target;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public AttackMetadata getAttack() {
        return attack;
    }

    public PlayerKillEntityEvent call() {
        EVENT.invoker().onPlayerKillEntity(this);
        return this;
    }
}
