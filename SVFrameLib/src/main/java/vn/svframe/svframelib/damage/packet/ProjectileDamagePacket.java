package vn.svframe.svframelib.damage.packet;
import vn.svframe.svframelib.damage.*;import net.minecraft.entity.projectile.ProjectileEntity;
public final class ProjectileDamagePacket extends DamagePacket {private final ProjectileEntity projectile;public ProjectileDamagePacket(double value,ProjectileEntity projectile,DamageType...types){super(value,types);this.projectile=projectile;}public ProjectileEntity getProjectile(){return projectile;}}
