package vn.svframe.svframelib.damage;

import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.provider.StatProvider;
import vn.svframe.svframelib.player.PlayerMetadata;
import net.minecraft.entity.LivingEntity;

/**
 * Native Fabric form of MythicLib 1.7.1 melee attack metadata.
 *
 * <p>The original Bukkit implementation only adds the action-hand lookup on
 * top of {@link AttackMetadata}. The same contract is retained here with
 * Fabric entity types.</p>
 */
public class MeleeAttackMetadata extends AttackMetadata {
    public MeleeAttackMetadata(DamageMetadata damage, LivingEntity target, StatProvider attacker) {
        super(damage, target, attacker);
    }

    public EquipmentSlot getHand() {
        StatProvider attacker = getAttacker();
        if (attacker == null) throw new IllegalArgumentException("No attacker found");
        if (!(attacker instanceof PlayerMetadata metadata)) {
            throw new IllegalArgumentException("Attacker is not a player");
        }
        return metadata.getActionHand();
    }
}
