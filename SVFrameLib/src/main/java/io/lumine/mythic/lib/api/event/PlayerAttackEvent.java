package io.lumine.mythic.lib.api.event;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.provider.PlayerStatProvider;
import io.lumine.mythic.lib.damage.AttackMetadata;
import io.lumine.mythic.lib.player.PlayerMetadata;
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
