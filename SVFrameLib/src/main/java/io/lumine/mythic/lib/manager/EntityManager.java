package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.comp.interaction.InteractionType;
import io.lumine.mythic.lib.comp.interaction.TargetRestriction;
import io.lumine.mythic.lib.comp.interaction.relation.Relationship;
import io.lumine.mythic.lib.comp.interaction.relation.RelationshipHandler;
import io.lumine.mythic.lib.entity.ProjectileMetadata;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Native Fabric entity/relationship registry preserving the 1.7.1 manager contract. */
public class EntityManager extends Module {
    private final Set<RelationshipHandler> relationshipHandlers = new CopyOnWriteArraySet<>();
    private final Set<TargetRestriction> restrictions = new CopyOnWriteArraySet<>();

    public EntityManager(MMOPlugin plugin) { super(plugin, "entity"); }

    public void registerRelationHandler(RelationshipHandler handler) {
        relationshipHandlers.add(Objects.requireNonNull(handler, "handler"));
    }

    public Set<RelationshipHandler> getRelationHandlers() {
        return Set.copyOf(relationshipHandlers);
    }

    public boolean canInteract(ServerPlayerEntity source, Entity target, InteractionType type) {
        if (source == null || target == null || type == null || source == target || !target.isAlive()) return false;
        if (!(target instanceof LivingEntity living) || target instanceof ArmorStandEntity) return false;
        for (TargetRestriction restriction : restrictions) if (!restriction.canTarget(source, living, type)) return false;
        if (target instanceof ServerPlayerEntity playerTarget) return checkPvpInteractionRules(source, playerTarget, type, true);
        // Support actions on mobs are allowed natively; integrations can layer stricter rules through flags/conditions.
        return true;
    }

    public boolean checkPvpInteractionRules(ServerPlayerEntity source,
                                            ServerPlayerEntity target,
                                            InteractionType type,
                                            boolean pvpAllowed) {
        if (source == null || target == null || type == null) return false;
        Relationship relationship = source == target ? Relationship.SELF : Relationship.GUILD_NEUTRAL;
        if (source != target) {
            for (RelationshipHandler handler : relationshipHandlers) {
                Relationship resolved = handler.getRelationship(source, target);
                if (resolved == null) continue;
                relationship = resolved;
                if (!isRelationshipAllowed(type, resolved, pvpAllowed)) return false;
            }
        }
        return isRelationshipAllowed(type, relationship, pvpAllowed);
    }

    private static boolean isRelationshipAllowed(InteractionType type, Relationship relationship, boolean pvpAllowed) {
        if (!type.isOffense()) return relationship != Relationship.GUILD_ENEMY;
        if (!pvpAllowed) return false;
        return relationship != Relationship.SELF && relationship != Relationship.PARTY_MEMBER && relationship != Relationship.GUILD_ALLY;
    }

    public void registerRestriction(TargetRestriction restriction) { restrictions.add(Objects.requireNonNull(restriction, "restriction")); }

    public void unregisterCustomProjectile(Entity projectile) {
        ProjectileMetadata metadata = ProjectileMetadata.get(projectile);
        if (metadata != null) metadata.close();
    }

    public ProjectileMetadata getCustomProjectile(Entity entity) {
        return ProjectileMetadata.get(entity);
    }

    public void registerCustomProjectile(Entity entity, ProjectileMetadata metadata) {
        if (entity == null || metadata == null) throw new IllegalArgumentException("Entity and metadata cannot be null");
        // Native ProjectileMetadata registers itself by UUID at construction.
        // Enforce that callers cannot associate metadata with a different entity.
        if (ProjectileMetadata.get(entity) != metadata)
            throw new IllegalArgumentException("Projectile metadata is not registered for entity " + entity.getUuid());
    }

    public boolean canTarget(ServerPlayerEntity source, Entity target, InteractionType type) {
        return canInteract(source, target, type);
    }
}
