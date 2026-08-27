package vn.svframe.svframelib.fabric;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import vn.svframe.svframelib.fabric.runtime.script.ScriptContext;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Exact native Fabric implementations for SVFrameLib 1.7.1 target/status built-ins. */
final class NativeTargetStatusSkillRuntime {
    private static final Set<String> IDS = Set.of("BLIND", "BURN", "POISON", "SLOW");
    private static final DustParticleEffect BLACK_DUST = new DustParticleEffect(new Vector3f(0.0F, 0.0F, 0.0F), 1.0F);
    private static final DustParticleEffect WHITE_DUST = new DustParticleEffect(new Vector3f(1.0F, 1.0F, 1.0F), 1.0F);

    private NativeTargetStatusSkillRuntime() { }

    static boolean supports(String id) {
        return IDS.contains(normalize(id));
    }

    static boolean cast(String id, ScriptContext context) {
        if (context == null) return false;
        return switch (normalize(id)) {
            case "BLIND" -> blind(context);
            case "BURN" -> burn(context);
            case "POISON" -> poison(context);
            case "SLOW" -> slow(context);
            default -> false;
        };
    }

    private static boolean blind(ScriptContext context) {
        LivingEntity target = living(context.target());
        Entity caster = entity(context.caster());
        if (target == null || caster == null) return false;
        ServerWorld world = (ServerWorld) target.getWorld();

        playSound(world, target, "minecraft:entity.enderman.hurt", 1.0F, 2.0F);
        Vec3d direction = caster.getRotationVec(1.0F);
        Vec3d origin = target.getPos();
        for (double angle = 0.0d; angle < Math.PI * 2.0d; angle += Math.PI / 24.0d) {
            for (double layer = 0.0d; layer < 2.0d; layer += 1.0d) {
                Vec3d local = new Vec3d(
                        Math.cos(angle),
                        1.0d + Math.cos(angle + Math.PI * layer) * 0.5d,
                        Math.sin(angle));
                Vec3d offset = rotate(local, direction);
                spawn(world, BLACK_DUST, origin.add(offset), 1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
        addEffect(target, "minecraft:blindness", NativeTargetStatusSemantics.durationTicks(parameter(context, "duration")), 0);
        return true;
    }

    private static boolean burn(ScriptContext context) {
        LivingEntity target = living(context.target());
        if (target == null) return false;
        ServerWorld world = (ServerWorld) target.getWorld();
        Vec3d origin = target.getPos();

        playBurnHelix(world, origin);
        playSound(world, target, "minecraft:entity.blaze.hurt", 1.0F, 2.0F);
        target.setOnFireForTicks(NativeTargetStatusSemantics.burnFireTicks(target.getFireTicks(), parameter(context, "duration")));
        return true;
    }

    private static boolean poison(ScriptContext context) {
        LivingEntity target = living(context.target());
        if (target == null) return false;
        ServerWorld world = (ServerWorld) target.getWorld();
        Vec3d point = target.getPos().add(0.0d, 1.0d, 0.0d);

        spawn(world, ParticleTypes.ITEM_SLIME, point, 32, 1.0d, 1.0d, 1.0d, 0.0d);
        spawn(world, ParticleTypes.HAPPY_VILLAGER, point, 24, 1.0d, 1.0d, 1.0d, 0.0d);
        playSound(world, target, "minecraft:block.brewing_stand.brew", 1.5F, 2.0F);
        addEffect(target, "minecraft:poison",
                NativeTargetStatusSemantics.durationTicks(parameter(context, "duration")),
                NativeTargetStatusSemantics.amplifier(parameter(context, "amplifier")));
        return true;
    }

    private static boolean slow(ScriptContext context) {
        LivingEntity target = living(context.target());
        if (target == null) return false;
        ServerWorld world = (ServerWorld) target.getWorld();

        playSlowSpiral(world, target.getPos());
        playSound(world, target, "minecraft:entity.llama.angry", 1.0F, 2.0F);
        addEffect(target, "minecraft:slowness",
                NativeTargetStatusSemantics.durationTicks(parameter(context, "duration")),
                NativeTargetStatusSemantics.amplifier(parameter(context, "amplifier")));
        return true;
    }

    private static void playBurnHelix(ServerWorld world, Vec3d origin) {
        final class BurnHelix implements Runnable {
            private double y;

            @Override
            public void run() {
                for (int step = 0; step < 3; step++) {
                    y += 0.04d;
                    for (int arm = 0; arm < 2; arm++) {
                        double angle = y * Math.PI * 1.3d + arm * Math.PI;
                        spawn(world, ParticleTypes.FLAME,
                                origin.add(Math.cos(angle), y, Math.sin(angle)),
                                1, 0.0d, 0.0d, 0.0d, 0.0d);
                    }
                }
                if (y < 1.7d) SVFrameLibFabricMod.schedule(1, this);
            }
        }
        SVFrameLibFabricMod.schedule(0, new BurnHelix());
    }

    private static void playSlowSpiral(ServerWorld world, Vec3d origin) {
        final class SlowSpiral implements Runnable {
            private double time;

            @Override
            public void run() {
                time += Math.PI / 10.0d;
                for (double phase = 0.0d; phase < Math.PI * 2.0d; phase += Math.PI) {
                    for (double radius = 0.0d; radius < 0.7d; radius += 0.1d) {
                        double angle = time / 2.0d + phase + Math.PI * radius;
                        spawn(world, WHITE_DUST,
                                origin.add(Math.cos(angle) * radius * 2.0d, 0.1d, Math.sin(angle) * radius * 2.0d),
                                1, 0.0d, 0.0d, 0.0d, 0.0d);
                    }
                }
                if (time < Math.PI * 2.0d) SVFrameLibFabricMod.schedule(1, this);
            }
        }
        SVFrameLibFabricMod.schedule(0, new SlowSpiral());
    }

    private static void addEffect(LivingEntity target, String effectId, int duration, int amplifier) {
        var effect = Registries.STATUS_EFFECT.getEntry(Identifier.of(effectId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown status effect: " + effectId));
        target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier));
    }

    private static void playSound(ServerWorld world, Entity target, String soundId, float volume, float pitch) {
        var sound = Registries.SOUND_EVENT.get(Identifier.of(soundId));
        world.playSound(null, target.getX(), target.getY(), target.getZ(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private static <T extends ParticleEffect> void spawn(ServerWorld world, T particle, Vec3d point, int count,
                                                         double dx, double dy, double dz, double speed) {
        world.spawnParticles(particle, point.x, point.y, point.z, count, dx, dy, dz, speed);
    }

    private static Vec3d rotate(Vec3d vector, Vec3d direction) {
        double yaw;
        double pitch;
        if (direction.x == 0.0d && direction.z == 0.0d) {
            yaw = 0.0d;
            pitch = direction.y > 0.0d ? -Math.PI / 2.0d : Math.PI / 2.0d;
        } else {
            yaw = (Math.atan2(-direction.x, direction.z) + Math.PI * 2.0d) % (Math.PI * 2.0d);
            pitch = Math.atan(-direction.y / Math.sqrt(direction.x * direction.x + direction.z * direction.z));
        }

        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double y = vector.y * cosPitch - vector.z * sinPitch;
        double z = vector.y * sinPitch + vector.z * cosPitch;

        double rotation = -yaw;
        double cosYaw = Math.cos(rotation);
        double sinYaw = Math.sin(rotation);
        double x = vector.x * cosYaw + z * sinYaw;
        double rotatedZ = -vector.x * sinYaw + z * cosYaw;
        return new Vec3d(x, y, rotatedZ);
    }

    private static double parameter(ScriptContext context, String key) {
        Double value = context.numbers().get(key);
        if (value == null) value = context.numbers().get("parameter." + key);
        if (value != null) return value;
        Object raw = context.objects().get(key);
        if (raw == null) raw = context.objects().get("parameter." + key);
        if (raw instanceof Number number) return number.doubleValue();
        if (raw == null) return 0.0d;
        return Double.parseDouble(String.valueOf(raw));
    }

    private static LivingEntity living(UUID id) {
        Entity found = entity(id);
        return found instanceof LivingEntity living ? living : null;
    }

    private static Entity entity(UUID id) {
        if (id == null) return null;
        MinecraftServer server = SVFrameLibFabricMod.server();
        if (server == null) return null;
        var player = server.getPlayerManager().getPlayer(id);
        if (player != null) return player;
        for (ServerWorld world : server.getWorlds()) {
            Entity found = world.getEntity(id);
            if (found != null) return found;
        }
        return null;
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
