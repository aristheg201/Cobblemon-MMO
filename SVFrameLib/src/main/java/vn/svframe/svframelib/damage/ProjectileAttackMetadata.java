package vn.svframe.svframelib.damage;

import vn.svframe.svframelib.api.stat.provider.StatProvider;
import vn.svframe.svframelib.entity.ProjectileMetadata;
import vn.svframe.svframelib.player.PlayerMetadata;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;

/** Native Fabric form of SVFrameLib 1.7.1 projectile attack metadata. */
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
