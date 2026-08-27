package vn.svframe.svframelib.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;

/** Native attack-source adapter contract mirroring SVFrameLib 1.7.1 fail-fast semantics. */
public interface AttackHandler {
    /** Native equivalent of the server-plugin platform damage-event entry point. */
    default AttackMetadata getAttack(LivingEntity target, DamageSource source, double damage) {
        return getAttack(target);
    }

    /** @deprecated Implement the native damage-source overload instead. */
    @Deprecated
    default AttackMetadata getAttack(Entity entity) {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    /** @deprecated Implement attack lookup instead. */
    @Deprecated
    default boolean isAttacked(Entity entity) {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    default boolean isFake(Object event) { return false; }
}
