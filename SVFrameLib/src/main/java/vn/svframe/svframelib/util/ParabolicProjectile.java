package vn.svframe.svframelib.util;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.Objects;
import java.util.function.Consumer;

/** Fabric-native port of MythicLib 1.7.1 ParabolicProjectile. */
public class ParabolicProjectile implements Runnable {
    private final ServerWorld world;
    private final Vec3d target;
    private final Consumer<Vec3d> display;
    private final Vec3d vector;
    private final Runnable end;
    private final int speed;

    private Vec3d location;
    private int step;
    private boolean active = true;

    public ParabolicProjectile(ServerWorld world, Vec3d source, Vec3d target, ParticleEffect particle) {
        this(world, source, target, () -> { }, 1, particle);
    }

    public ParabolicProjectile(ServerWorld world, Vec3d source, Vec3d target, Runnable end, ParticleEffect particle) {
        this(world, source, target, end, 1, particle);
    }

    public ParabolicProjectile(ServerWorld world, Vec3d source, Vec3d target, Runnable end, int speed, ParticleEffect particle) {
        this(world, source, target, defaultVector(source, target), end, speed,
                point -> world.spawnParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0));
    }

    public ParabolicProjectile(ServerWorld world, Vec3d source, Vec3d target, Vec3d vector, Runnable end, int speed, ParticleEffect particle) {
        this(world, source, target, vector, end, speed,
                point -> world.spawnParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0));
    }

    public ParabolicProjectile(ServerWorld world, Vec3d source, Vec3d target, Vec3d vector, Runnable end, int speed, Consumer<Vec3d> display) {
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.location = Objects.requireNonNull(source, "Source cannot be null");
        this.target = Objects.requireNonNull(target, "Target cannot be null");
        this.vector = Objects.requireNonNull(vector, "Vector cannot be null");
        this.end = end == null ? () -> { } : end;
        this.speed = Math.max(1, speed);
        this.display = Objects.requireNonNull(display, "Display cannot be null");
        MythicLibFabricMod.schedule(0, this);
    }

    @Override
    public void run() {
        if (!active) return;

        for (int i = 0; i < speed; i++) {
            step++;
            if (step > 100 || location.squaredDistanceTo(target) < 0.8d) {
                active = false;
                end.run();
                return;
            }

            double blend = Math.min(1d, step / 40d);
            Vec3d toward = target.subtract(location);
            if (toward.lengthSquared() > 1.0E-12d) toward = toward.normalize();
            Vec3d movement = toward.multiply(blend).add(vector.multiply(1d - blend));
            location = location.add(movement);
            display.accept(location);
        }

        MythicLibFabricMod.schedule(1, this);
    }

    public boolean isActive() {
        return active;
    }

    public Vec3d getLocation() {
        return location;
    }

    public ServerWorld getWorld() {
        return world;
    }

    private static Vec3d defaultVector(Vec3d source, Vec3d target) {
        Objects.requireNonNull(source, "Source cannot be null");
        Objects.requireNonNull(target, "Target cannot be null");
        Vec3d raw = target.subtract(source).multiply(0.1d);
        Vec3d lifted = new Vec3d(raw.x, 6d, raw.z);
        return lifted.lengthSquared() < 1.0E-12d ? Vec3d.ZERO : lifted.normalize().multiply(0.3d);
    }
}
