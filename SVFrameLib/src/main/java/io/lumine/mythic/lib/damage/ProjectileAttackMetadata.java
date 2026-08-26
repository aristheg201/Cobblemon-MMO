package io.lumine.mythic.lib.damage;

import io.lumine.mythic.lib.api.stat.provider.StatProvider;
import io.lumine.mythic.lib.entity.ProjectileMetadata;
import io.lumine.mythic.lib.player.PlayerMetadata;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;

/** Native Fabric form of MythicLib 1.7.1 projectile attack metadata. */
public class ProjectileAttackMetadata extends AttackMetadata {
    private final ProjectileEntity projectile;
    private final ProjectileMetadata projectileMetadata;

    public ProjectileAttackMetadata(DamageMetadata damage,
                                    PlayerMetadata attacker,
                                    ProjectileEntity projectile) {
        this(damage, null, attacker, projectile, null);
    }

    public ProjectileAttackMetadata(DamageMetadata damage,
                                    LivingEntity target,
                                    StatProvider attacker,
                                    ProjectileEntity projectile) {
        this(damage, target, attacker, projectile, null);
    }

    public ProjectileAttackMetadata(DamageMetadata damage,
                                    LivingEntity target,
                                    StatProvider attacker,
                                    ProjectileEntity projectile,
                                    ProjectileMetadata projectileMetadata) {
        super(damage, target, attacker);
        this.projectile = projectile;
        this.projectileMetadata = projectileMetadata;
    }

    public ProjectileEntity getProjectile() {
        return projectile;
    }

    public ProjectileMetadata getProjectileMetadata() {
        return projectileMetadata;
    }
}
