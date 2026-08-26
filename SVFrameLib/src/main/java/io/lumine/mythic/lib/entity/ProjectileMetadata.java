package io.lumine.mythic.lib.entity;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.api.event.PlayerAttackEvent;
import io.lumine.mythic.lib.api.item.NBTItem;
import io.lumine.mythic.lib.api.player.EquipmentSlot;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.damage.DamageType;
import io.lumine.mythic.lib.damage.ProjectileAttackMetadata;
import io.lumine.mythic.lib.player.PlayerMetadata;
import io.lumine.mythic.lib.player.skill.PassiveSkill;
import io.lumine.mythic.lib.skill.trigger.TriggerMetadata;
import io.lumine.mythic.lib.util.TemporaryHandler;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Fabric-native projectile metadata retaining MythicLib 1.7.1 projectile
 * snapshot, damage and passive-trigger semantics.
 *
 * <p>Bukkit attached metadata is replaced by a UUID registry because Fabric
 * entities do not expose Bukkit's metadata container. The lifecycle remains
 * owned by the shooter MMOPlayerData through {@link TemporaryHandler}, so
 * disconnect/session cleanup closes the metadata exactly like the original
 * listener-backed handler.</p>
 */
public class ProjectileMetadata extends TemporaryHandler {
    public static final String METADATA_KEY = "MythicLibProjectileMetadata";

    private static final ConcurrentMap<UUID, ProjectileMetadata> ACTIVE = new ConcurrentHashMap<>();

    private final int entityId;
    private final UUID entityUuid;
    private final ProjectileType projectileType;
    private final List<PassiveSkill> cachedSkills;
    private final PlayerMetadata shooter;
    private final TriggerMetadata tickTriggerMetadata;
    private final List<DamageType> damageTypes;

    private NBTItem sourceItem;
    private boolean customDamage;
    private double damageMultiplier = 1d;

    private ProjectileMetadata(PlayerMetadata shooter,
                               List<DamageType> damageTypes,
                               ProjectileType projectileType,
                               Entity projectile) {
        super(Objects.requireNonNull(shooter, "Shooter cannot be null").getData());
        Objects.requireNonNull(projectile, "Projectile cannot be null");
        this.entityId = projectile.getId();
        this.entityUuid = projectile.getUuid();
        this.projectileType = Objects.requireNonNull(projectileType, "Projectile type cannot be null");
        this.damageTypes = new ArrayList<>(Objects.requireNonNull(damageTypes, "Damage types cannot be null"));
        this.shooter = shooter;
        this.cachedSkills = new ArrayList<>(shooter.getData().getPassiveSkillMap().isolateModifiers(shooter.getActionHand()));
        this.tickTriggerMetadata = new TriggerMetadata(shooter, projectileType.getTickTrigger(), projectile, null);
        ACTIVE.put(entityUuid, this);
    }

    public PlayerMetadata getShooter() {
        return shooter;
    }

    public List<DamageType> getDamageTypes() {
        return damageTypes;
    }

    public NBTItem getSourceItem() {
        return sourceItem;
    }

    public void setSourceItem(NBTItem sourceItem) {
        this.sourceItem = sourceItem;
    }

    public List<PassiveSkill> getEffectiveSkills() {
        return cachedSkills;
    }

    public boolean isCustomDamage() {
        return customDamage;
    }

    public void setCustomDamage(boolean customDamage) {
        this.customDamage = customDamage;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(double damageMultiplier) {
        if (!Double.isFinite(damageMultiplier) || damageMultiplier < 0d) {
            throw new IllegalArgumentException("Damage multiplier must be positive");
        }
        this.damageMultiplier = damageMultiplier;
    }

    public double getDamage() {
        return shooter.getStat("ATTACK_DAMAGE") * damageMultiplier;
    }

    /** Called by the native projectile mixin once per server tick. */
    public void triggerTick() {
        if (!isOpen()) return;
        shooter.getData().triggerSkills(tickTriggerMetadata, cachedSkills, false);
    }

    /** Native equivalent of the 1.7.1 PlayerAttackEvent projectile-hit listener. */
    public void triggerHit(PlayerAttackEvent event) {
        if (!isOpen() || event == null || !(event.getAttack() instanceof ProjectileAttackMetadata attack)) return;
        if (attack.getProjectile() == null || attack.getProjectile().getId() != entityId) return;
        triggerHit(event.getEntity());
    }

    /** Native projectile collision path used by the Fabric mixin. */
    public void triggerHit(Entity target) {
        if (!isOpen() || target == null) return;
        TriggerMetadata metadata = new TriggerMetadata(shooter, projectileType.getHitTrigger(), target, null);
        shooter.getData().triggerSkills(metadata, cachedSkills);
    }

    /** Native block-land trigger used by the Fabric mixin. */
    public void triggerLand(Entity projectile) {
        if (!isOpen() || projectile == null || projectile.getId() != entityId) return;
        TriggerMetadata metadata = new TriggerMetadata(shooter, projectileType.getLandTrigger(), projectile, null);
        shooter.getData().triggerSkills(metadata, cachedSkills);
    }

    /** Schedule closure after a projectile hit, matching Bukkit's next-tick unregister. */
    public void unregisterOnHit(Entity projectile) {
        if (matches(projectile)) closeAfter(1L);
    }

    public void unregisterOnDeath(Entity projectile) {
        if (matches(projectile)) close();
    }

    public void unregisterOnLogout(ServerPlayerEntity player) {
        if (player != null && player.getUuid().equals(shooter.getData().getUniqueId())) close();
    }

    private boolean matches(Entity projectile) {
        return projectile != null && projectile.getId() == entityId && projectile.getUuid().equals(entityUuid);
    }

    @Override
    protected void onClose() {
        ACTIVE.remove(entityUuid, this);
    }

    public static ProjectileMetadata get(Entity projectile) {
        if (projectile == null) return null;
        ProjectileMetadata metadata = ACTIVE.get(projectile.getUuid());
        if (metadata == null) return null;
        if (!metadata.isOpen() || metadata.entityId != projectile.getId()) {
            ACTIVE.remove(projectile.getUuid(), metadata);
            return null;
        }
        return metadata;
    }

    public static ProjectileMetadata create(PlayerMetadata shooter,
                                            ProjectileType projectileType,
                                            Entity projectile) {
        ProjectileMetadata existing = get(projectile);
        if (existing != null) return existing;
        return new ProjectileMetadata(shooter, MythicLib.plugin.getMMOConfig().bowAttackTypes, projectileType, projectile);
    }

    public static ProjectileMetadata create(MMOPlayerData data,
                                            EquipmentSlot actionHand,
                                            ProjectileType projectileType,
                                            Entity projectile) {
        ProjectileMetadata existing = get(projectile);
        if (existing != null) return existing;
        PlayerMetadata shooter = data.getStatMap().cache(actionHand);
        return create(shooter, MythicLib.plugin.getMMOConfig().bowAttackTypes, projectileType, projectile);
    }

    public static ProjectileMetadata create(PlayerMetadata shooter,
                                            List<DamageType> attackDamageTypes,
                                            ProjectileType projectileType,
                                            Entity projectile) {
        ProjectileMetadata existing = get(projectile);
        if (existing != null) return existing;
        return new ProjectileMetadata(shooter, attackDamageTypes, projectileType, projectile);
    }

    /** @deprecated retained for 1.7.1 source compatibility. */
    @Deprecated
    public static ProjectileMetadata getCustomData(Entity projectile) {
        return get(projectile);
    }
}
