package vn.svframe.svframelib.damage;

import net.minecraft.entity.Entity;

/** Native attack-source adapter contract. */
public interface AttackHandler {
    default AttackMetadata getAttack(Entity entity) { return null; }
    default boolean isAttacked(Entity entity) { return getAttack(entity) != null; }
    default boolean isFake(Object event) { return false; }
}
