package io.lumine.mythic.lib.util;

import io.lumine.mythic.lib.api.player.EquipmentSlot;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Native Fabric implementation of MythicLib 1.7.1 RayTrace.
 *
 * <p>Semantics retained from the original Bukkit implementation: fluids are
 * ignored, collision shapes stop the ray, entities use a 0.2 block ray size,
 * and distanceTraveled is the distance to the first block even when no living
 * entity is hit.</p>
 */
public class RayTrace {
    private static final double RAY_SIZE = 0.2d;

    private final LivingEntity hitEntity;
    private final double distanceTraveled;
    private final ServerWorld initialWorld;
    private final Vec3d initialLocation;
    private final Vec3d initialDirection;

    public RayTrace(ServerPlayerEntity player, double range, Predicate<Entity> predicate) {
        this(player, player.getRotationVec(1.0F), range, predicate);
    }

    public RayTrace(ServerPlayerEntity player, Vec3d direction, double range, Predicate<Entity> predicate) {
        this((ServerWorld) Objects.requireNonNull(player, "Player cannot be null").getWorld(),
                player.getEyePos(), direction, range, predicate);
    }

    public RayTrace(ServerPlayerEntity player, EquipmentSlot slot, double range, Predicate<Entity> predicate) {
        Objects.requireNonNull(player, "Player cannot be null");
        Objects.requireNonNull(slot, "Equipment slot cannot be null");
        if (!slot.isHand()) throw new IllegalArgumentException("Not a hand equipment slot");

        double yaw = Math.toRadians(player.getYaw() + 90d + 45d * (slot == EquipmentSlot.MAIN_HAND ? 1d : -1d));
        Vec3d origin = player.getPos().add(Math.cos(yaw) * 0.5d, 1.5d, Math.sin(yaw) * 0.5d);
        TraceResult result = trace((ServerWorld) player.getWorld(), origin, player.getRotationVec(1.0F), range, predicate);
        this.initialWorld = (ServerWorld) player.getWorld();
        this.initialLocation = origin;
        this.initialDirection = normalized(player.getRotationVec(1.0F));
        this.hitEntity = result.entity();
        this.distanceTraveled = result.distance();
    }

    public RayTrace(ServerWorld world, Vec3d location, Vec3d direction, double range, Predicate<Entity> predicate) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(location, "Location cannot be null");
        Objects.requireNonNull(direction, "Direction cannot be null");
        if (!Double.isFinite(range) || range < 0d) throw new IllegalArgumentException("Range cannot be negative");

        this.initialWorld = world;
        this.initialLocation = location;
        this.initialDirection = normalized(direction);
        TraceResult result = trace(world, location, this.initialDirection, range, predicate);
        this.hitEntity = result.entity();
        this.distanceTraveled = result.distance();
    }

    public boolean hasHit() {
        return hitEntity != null;
    }

    public LivingEntity getHit() {
        return hitEntity;
    }

    public double getDistanceTraveled() {
        return distanceTraveled;
    }

    public void draw(double rate, ParticleEffect particle) {
        Objects.requireNonNull(particle, "Particle cannot be null");
        draw(rate, point -> initialWorld.spawnParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0));
    }

    public void draw(double rate, Consumer<Vec3d> consumer) {
        Objects.requireNonNull(consumer, "Consumer cannot be null");
        if (!Double.isFinite(rate) || rate <= 0d) throw new IllegalArgumentException("Rate must be positive");
        for (double distance = 0d; distance < distanceTraveled; distance += rate) {
            consumer.accept(initialLocation.add(initialDirection.multiply(distance)));
        }
    }

    private static TraceResult trace(ServerWorld world,
                                     Vec3d origin,
                                     Vec3d direction,
                                     double range,
                                     Predicate<Entity> predicate) {
        if (range == 0d) return new TraceResult(null, 0d);
        Vec3d normalized = normalized(direction);
        Vec3d end = origin.add(normalized.multiply(range));

        BlockHitResult block = world.raycast(new RaycastContext(
                origin,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                ShapeContext.absent()));

        double bestDistance = block.getType() == HitResult.Type.MISS
                ? range
                : Math.min(range, origin.distanceTo(block.getPos()));
        LivingEntity bestEntity = null;

        double minX = Math.min(origin.x, end.x) - RAY_SIZE;
        double minY = Math.min(origin.y, end.y) - RAY_SIZE;
        double minZ = Math.min(origin.z, end.z) - RAY_SIZE;
        double maxX = Math.max(origin.x, end.x) + RAY_SIZE;
        double maxY = Math.max(origin.y, end.y) + RAY_SIZE;
        double maxZ = Math.max(origin.z, end.z) + RAY_SIZE;
        Box search = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        Predicate<Entity> filter = predicate == null ? ignored -> true : predicate;

        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class, search,
                candidate -> candidate.isAlive() && filter.test(candidate))) {
            Box hitBox = entity.getBoundingBox().expand(RAY_SIZE);
            double distance;
            if (hitBox.contains(origin)) {
                distance = 0d;
            } else {
                Optional<Vec3d> intersection = hitBox.raycast(origin, end);
                if (intersection.isEmpty()) continue;
                distance = origin.distanceTo(intersection.get());
            }
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestEntity = entity;
            }
        }

        return new TraceResult(bestEntity, bestDistance);
    }

    private static Vec3d normalized(Vec3d direction) {
        if (direction.lengthSquared() < 1.0E-12d) throw new IllegalArgumentException("Direction cannot be zero");
        return direction.normalize();
    }

    private record TraceResult(LivingEntity entity, double distance) { }
}
