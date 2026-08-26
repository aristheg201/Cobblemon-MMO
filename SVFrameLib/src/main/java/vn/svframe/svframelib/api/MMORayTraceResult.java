package vn.svframe.svframelib.api;

import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;
import java.util.function.Consumer;

/** Native Fabric equivalent of MythicLib 1.7.1 MMORayTraceResult. */
public class MMORayTraceResult {
    private final LivingEntity entity;
    private final double range;

    public MMORayTraceResult(LivingEntity entity, double range) {
        this.range = range;
        this.entity = entity;
    }

    public boolean hasHit() {
        return entity != null;
    }

    public LivingEntity getHit() {
        return entity;
    }

    public double getRange() {
        return range;
    }

    public void draw(ServerWorld world, Vec3d location, Vec3d direction, double rate, ParticleEffect particle) {
        Objects.requireNonNull(world, "World cannot be null");
        Objects.requireNonNull(particle, "Particle cannot be null");
        draw(location, direction, rate, point -> world.spawnParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0));
    }

    public void draw(Vec3d location, Vec3d direction, double rate, Consumer<Vec3d> consumer) {
        Objects.requireNonNull(location, "Location cannot be null");
        Objects.requireNonNull(direction, "Direction cannot be null");
        Objects.requireNonNull(consumer, "Consumer cannot be null");
        if (!Double.isFinite(rate) || rate <= 0d) throw new IllegalArgumentException("Rate must be positive");
        if (!Double.isFinite(range) || range <= 0d) return;

        Vec3d step = direction.normalize().multiply(1d / rate);
        Vec3d point = location;
        for (int i = 0; i < range * rate; i++) {
            point = point.add(step);
            consumer.accept(point);
        }
    }
}
