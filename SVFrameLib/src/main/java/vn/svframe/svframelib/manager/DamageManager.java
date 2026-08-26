package vn.svframe.svframelib.manager;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.svframelib.api.event.AttackEvent;
import vn.svframe.svframelib.api.event.PlayerAttackEvent;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.provider.StatProvider;
import vn.svframe.svframelib.damage.*;
import vn.svframe.svframelib.entity.ProjectileMetadata;
import vn.svframe.svframelib.module.MMOPlugin;
import vn.svframe.svframelib.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/** Native Fabric implementation of the MythicLib 1.7.1 damage registry and attack reconstruction. */
public class DamageManager extends Module {
    private static final Identifier NO_KNOCKBACK_ID = Identifier.of("svframelib", "no_knockback");
    private static final EntityAttributeModifier NO_KNOCKBACK = new EntityAttributeModifier(
            NO_KNOCKBACK_ID, 100d, EntityAttributeModifier.Operation.ADD_VALUE);

    private final List<AttackHandler> handlers = new ArrayList<>();
    private final Map<UUID, AttackMetadata> attackMetadatas = new WeakHashMap<>();

    public DamageManager(MMOPlugin plugin) { super(plugin, "damage"); }

    public synchronized void registerHandler(AttackHandler handler) {
        handlers.add(Objects.requireNonNull(handler, "Damage handler cannot be null"));
    }

    public synchronized List<AttackHandler> getHandlers() { return List.copyOf(handlers); }

    public boolean registerAttack(AttackMetadata attack) { return registerAttack(attack, true, false); }

    /** The boolean is knockback, exactly as in MythicLib 1.7.1. */
    public boolean registerAttack(AttackMetadata attack, boolean knockback) {
        return registerAttack(attack, knockback, false);
    }

    /**
     * Registers and applies custom damage. The booleans are knockback and
     * ignoreImmunity, not an apply-damage flag.
     */
    public boolean registerAttack(AttackMetadata attack, boolean knockback, boolean ignoreImmunity) {
        Objects.requireNonNull(attack, "Attack cannot be null");
        if (attack.getTarget() == null) throw new IllegalArgumentException("Target cannot be null");
        markAsMetadata(attack);
        try {
            AttackEvent event = attack.isPlayer() ? new PlayerAttackEvent(attack) : new AttackEvent(attack);
            event.call();
            if (event.isCancelled()) return false;
            LivingEntity attacker = attack.hasAttacker() ? attack.getAttacker().getEntity() : null;
            return applyDamage(attack.getDamage().getDamage(), attack.getTarget(), attacker, knockback, ignoreImmunity);
        } finally {
            unmarkAsMetadata(attack.getTarget());
        }
    }

    private boolean applyDamage(double amount, LivingEntity target, LivingEntity attacker,
                                boolean knockback, boolean ignoreImmunity) {
        if (!Double.isFinite(amount) || amount <= 0d) throw new IllegalArgumentException("Damage must be strictly positive");
        Objects.requireNonNull(target, "Target cannot be null");

        if (!knockback) {
            EntityAttributeInstance resistance = target.getAttributeInstance(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
            if (resistance != null) {
                EntityAttributeModifier previous = resistance.getModifier(NO_KNOCKBACK_ID);
                if (previous != null) resistance.removeModifier(NO_KNOCKBACK_ID);
                resistance.addTemporaryModifier(NO_KNOCKBACK);
                try {
                    return applyDamage(amount, target, attacker, true, ignoreImmunity);
                } finally {
                    resistance.removeModifier(NO_KNOCKBACK_ID);
                    if (previous != null) resistance.addTemporaryModifier(previous);
                }
            }
        }

        if (ignoreImmunity) {
            int previous = target.timeUntilRegen;
            try {
                target.timeUntilRegen = 0;
                return applyDamage(amount, target, attacker, true, false);
            } finally {
                target.timeUntilRegen = previous;
            }
        }

        DamageSource source;
        if (attacker instanceof ServerPlayerEntity player) source = target.getDamageSources().playerAttack(player);
        else if (attacker != null) source = target.getDamageSources().mobAttack(attacker);
        else source = target.getDamageSources().generic();
        return target.damage(source, (float) Math.min(Float.MAX_VALUE, amount));
    }

    /** Native equivalent of MythicLib's EntityDamageEvent attack reconstruction. */
    public AttackMetadata findAttack(LivingEntity target, DamageSource source, double damage) {
        Objects.requireNonNull(target, "Target entity is not living");
        Objects.requireNonNull(source, "Damage source cannot be null");

        AttackMetadata found = getRegisteredAttackMetadata(target);
        if (found != null) return found;

        for (AttackHandler handler : getHandlers()) {
            found = handler.getAttack(target, source, damage);
            if (found != null) {
                markAsMetadata(found);
                return found;
            }
        }

        Entity direct = source.getSource();
        if (direct instanceof ProjectileEntity projectile) {
            ProjectileMetadata projectileData = ProjectileMetadata.get(projectile);
            if (projectileData != null) {
                found = new ProjectileAttackMetadata(
                        new DamageMetadata(damage, DamageType.WEAPON, DamageType.PHYSICAL, DamageType.PROJECTILE),
                        target, projectileData.getShooter(), projectile, projectileData);
                markAsMetadata(found);
                return found;
            }

            Entity attackerEntity = source.getAttacker();
            if (attackerEntity instanceof LivingEntity attacker && attacker != target) {
                StatProvider provider = StatProvider.get(attacker, EquipmentSlot.MAIN_HAND, true);
                found = new ProjectileAttackMetadata(
                        new DamageMetadata(damage, DamageType.WEAPON, DamageType.PHYSICAL, DamageType.PROJECTILE),
                        target, provider, projectile);
                markAsMetadata(found);
                return found;
            }
        }

        Entity attackerEntity = source.getAttacker();
        if (attackerEntity instanceof LivingEntity attacker) {
            StatProvider provider = StatProvider.get(attacker, EquipmentSlot.MAIN_HAND, true);
            found = new MeleeAttackMetadata(
                    new DamageMetadata(damage, getVanillaDamageTypes(attacker, source, EquipmentSlot.MAIN_HAND)),
                    target, provider);
            markAsMetadata(found);
            return found;
        }

        found = new AttackMetadata(new DamageMetadata(damage, getVanillaDamageTypes(source)), target, null);
        markAsMetadata(found);
        return found;
    }

    /** Backwards-compatible lookup for already-registered metadata. */
    public AttackMetadata findAttack(Object event) {
        if (event instanceof Entity entity) return getRegisteredAttackMetadata(entity);
        return null;
    }

    public List<DamageType> getVanillaDamageTypes(DamageSource source) {
        if (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC) || source.isOf(DamageTypes.DRAGON_BREATH))
            return List.of(DamageType.MAGIC);
        if (source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.WITHER_SKULL))
            return List.of(DamageType.MAGIC, DamageType.DOT);
        if (source.isOf(DamageTypes.ON_FIRE)) return List.of(DamageType.PHYSICAL, DamageType.DOT);
        if (source.isOf(DamageTypes.STARVE) || source.isOf(DamageTypes.DRY_OUT) || source.isOf(DamageTypes.FREEZE))
            return List.of(DamageType.DOT);
        if (source.getSource() instanceof ProjectileEntity || source.isOf(DamageTypes.ARROW) || source.isOf(DamageTypes.TRIDENT)
                || source.isOf(DamageTypes.MOB_PROJECTILE) || source.isOf(DamageTypes.FIREWORKS) || source.isOf(DamageTypes.THROWN)
                || source.isOf(DamageTypes.FIREBALL) || source.isOf(DamageTypes.UNATTRIBUTED_FIREBALL))
            return List.of(DamageType.PHYSICAL, DamageType.PROJECTILE);
        if (source.isOf(DamageTypes.IN_FIRE) || source.isOf(DamageTypes.CAMPFIRE) || source.isOf(DamageTypes.LAVA)
                || source.isOf(DamageTypes.HOT_FLOOR) || source.isOf(DamageTypes.SONIC_BOOM) || source.isOf(DamageTypes.LIGHTNING_BOLT)
                || source.isOf(DamageTypes.FALL) || source.isOf(DamageTypes.THORNS) || source.isOf(DamageTypes.CACTUS)
                || source.isOf(DamageTypes.SWEET_BERRY_BUSH) || source.isOf(DamageTypes.EXPLOSION) || source.isOf(DamageTypes.PLAYER_EXPLOSION)
                || source.isOf(DamageTypes.BAD_RESPAWN_POINT) || source.isOf(DamageTypes.FALLING_ANVIL) || source.isOf(DamageTypes.FALLING_BLOCK)
                || source.isOf(DamageTypes.FALLING_STALACTITE) || source.isOf(DamageTypes.FLY_INTO_WALL) || source.isOf(DamageTypes.IN_WALL)
                || source.isOf(DamageTypes.CRAMMING) || source.isOf(DamageTypes.DROWN) || source.isOf(DamageTypes.PLAYER_ATTACK)
                || source.isOf(DamageTypes.MOB_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO))
            return List.of(DamageType.PHYSICAL);
        return List.of();
    }

    public List<DamageType> getVanillaDamageTypes(LivingEntity damager, DamageSource source, EquipmentSlot hand) {
        Objects.requireNonNull(damager, "Damager cannot be null");
        if (!(source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK) || source.isOf(DamageTypes.MOB_ATTACK_NO_AGGRO)))
            return List.of(DamageType.PHYSICAL);
        ItemStack weapon = source.getWeaponStack();
        if (weapon == null || weapon.isEmpty()) return List.of(DamageType.UNARMED, DamageType.PHYSICAL);
        if (weapon.isDamageable()) return List.of(DamageType.WEAPON, DamageType.PHYSICAL);
        return List.of(DamageType.PHYSICAL);
    }

    public synchronized AttackMetadata markAsMetadata(AttackMetadata attack) {
        Objects.requireNonNull(attack, "Attack metadata cannot be null");
        AttackMetadata previous = attackMetadatas.put(attack.getTarget().getUuid(), attack);
        if (previous != null) getPlugin().logger().warning("Persistent attack metadata was found for " + attack.getTarget().getUuid());
        return previous;
    }

    public synchronized AttackMetadata unmarkAsMetadata(Entity entity) {
        return entity == null ? null : attackMetadatas.remove(entity.getUuid());
    }

    public synchronized AttackMetadata getRegisteredAttackMetadata(Entity entity) {
        return entity == null ? null : attackMetadatas.get(entity.getUuid());
    }

    public void damage(AttackMetadata attack, LivingEntity target) { damage(attack, target, true); }

    /** The boolean is knockback, matching the deprecated 1.7.1 API. */
    public void damage(AttackMetadata attack, LivingEntity target, boolean knockback) {
        damage(attack, target, knockback, false);
    }

    public void damage(AttackMetadata attack, LivingEntity target, boolean knockback, boolean ignoreImmunity) {
        registerAttack(retarget(attack, target), knockback, ignoreImmunity);
    }

    private AttackMetadata retarget(AttackMetadata attack, LivingEntity target) {
        Objects.requireNonNull(attack, "Attack cannot be null");
        Objects.requireNonNull(target, "Target cannot be null");
        if (attack.getTarget() == target) return attack;
        if (attack instanceof ProjectileAttackMetadata projectile)
            return new ProjectileAttackMetadata(attack.getDamage(), target, attack.getAttacker(), projectile.getProjectile(), projectile.getProjectileMetadata());
        if (attack instanceof MeleeAttackMetadata)
            return new MeleeAttackMetadata(attack.getDamage(), target, attack.getAttacker());
        return new AttackMetadata(attack.getDamage(), target, attack.getAttacker());
    }

    public DamageMetadata findDamage(Object event) {
        AttackMetadata attack = findAttack(event);
        return attack == null ? null : attack.getDamage();
    }

    public void unmarkAsMetadata(AttackMetadata attack) {
        if (attack != null && attack.getTarget() != null) unmarkAsMetadata(attack.getTarget());
    }
}
