package vn.svframe.svframelib.api.event;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.provider.PlayerStatProvider;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.player.PlayerMetadata;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;

/** Fabric-native player specialization of AttackEvent. */
public class PlayerAttackEvent extends AttackEvent {
    @FunctionalInterface
    public interface Listener {
        void onPlayerAttack(PlayerAttackEvent event);
    }

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> event -> {
                for (Listener listener : listeners) listener.onPlayerAttack(event);
            });

    private final PlayerMetadata attacker;

    public PlayerAttackEvent(AttackMetadata attack) {
        super(attack);
        if (!attack.isPlayer()) {
            throw new IllegalArgumentException("Not a player attack");
        }
        this.attacker = ((PlayerStatProvider) attack.getAttacker()).cache(EquipmentSlot.MAIN_HAND);
    }

    public PlayerMetadata getAttacker() {
        return attacker;
    }

    public MMOPlayerData getData() {
        return attacker.getData();
    }

    public ServerPlayerEntity getPlayer() {
        return attacker.getPlayer();
    }

    @Override
    public PlayerAttackEvent call() {
        super.call();
        EVENT.invoker().onPlayerAttack(this);
        return this;
    }
}
