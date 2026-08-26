package io.lumine.mythic.lib.player.particle;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.util.configobject.ConfigObject;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Native particle descriptor preserving the display API used by MythicLib particle effects. */
public class ParticleInformation {
    private static final Map<String,String> LEGACY = Map.ofEntries(
            Map.entry("REDSTONE", "dust"), Map.entry("DUST", "dust"), Map.entry("FLAME", "flame"),
            Map.entry("SMOKE_NORMAL", "smoke"), Map.entry("SMOKE_LARGE", "large_smoke"),
            Map.entry("SPELL_WITCH", "witch"), Map.entry("SPELL", "effect"), Map.entry("SPELL_MOB", "entity_effect"),
            Map.entry("CRIT", "crit"), Map.entry("CRIT_MAGIC", "enchanted_hit"), Map.entry("ENCHANTMENT_TABLE", "enchant"),
            Map.entry("END_ROD", "end_rod"), Map.entry("PORTAL", "portal"), Map.entry("CLOUD", "cloud"),
            Map.entry("HEART", "heart"), Map.entry("VILLAGER_HAPPY", "happy_villager"), Map.entry("VILLAGER_ANGRY", "angry_villager"),
            Map.entry("SOUL", "soul"), Map.entry("SOUL_FIRE_FLAME", "soul_fire_flame"), Map.entry("ELECTRIC_SPARK", "electric_spark")
    );

    private final ParticleEffect particle;
    private final int amount;
    private final double xOffset, yOffset, zOffset, speed;

    public ParticleInformation(ParticleEffect particle) { this(particle, 1, 0f, 0d, 0d, 0d); }
    public ParticleInformation(ParticleEffect particle, int amount, float speed, double offset, Object ignoredData) {
        this(particle, amount, speed, offset, offset, offset);
    }
    public ParticleInformation(ParticleEffect particle, int amount, float speed, double xOffset, double yOffset, double zOffset, Object ignoredData) {
        this(particle, amount, speed, xOffset, yOffset, zOffset);
    }
    public ParticleInformation(ParticleEffect particle, int amount, double speed, double xOffset, double yOffset, double zOffset) {
        this.particle = Objects.requireNonNull(particle, "particle");
        this.amount = Math.max(0, amount);
        this.speed = speed;
        this.xOffset = xOffset; this.yOffset = yOffset; this.zOffset = zOffset;
    }

    public ParticleEffect particle() { return particle; }

    public void display(ServerWorld world, Vec3d pos) { display(world, pos, amount, xOffset, yOffset, zOffset, speed); }
    public void display(ServerWorld world, Vec3d pos, double speed) { display(world, pos, amount, xOffset, yOffset, zOffset, speed); }
    public void display(ServerWorld world, Vec3d pos, int amount, double x, double y, double z, double speed) {
        world.spawnParticles(particle, pos.x, pos.y, pos.z, Math.max(0, amount), x, y, z, speed);
    }

    public static ParticleInformation fromConfig(Object raw) {
        if (raw instanceof ParticleInformation info) return info;
        if (!(raw instanceof ConfigObject obj)) throw new IllegalArgumentException("Particle config must be a ConfigObject");
        String name = obj.getString("name", "FLAME");
        int amount = obj.getInt("amount", 1);
        double offset = obj.getDouble("offset", obj.getDouble("r-offset", 0d));
        double x = obj.getDouble("x-offset", offset), y = obj.getDouble("y-offset", offset), z = obj.getDouble("z-offset", offset);
        double speed = obj.getDouble("speed", 0d);
        return new ParticleInformation(resolve(name, obj), amount, speed, x, y, z);
    }

    public static ParticleInformation of(ParticleEffect particle) { return new ParticleInformation(particle); }

    private static ParticleEffect resolve(String raw, ConfigObject config) {
        String enumName = UtilityMethods.enumName(raw);
        String path = LEGACY.getOrDefault(enumName, raw.toLowerCase(Locale.ROOT).replace(' ', '_'));
        if ("dust".equals(path)) {
            int red = 255, green = 0, blue = 0;
            if (config.contains("color")) {
                ConfigObject color = config.getObject("color");
                red = color.getInt("red", red); green = color.getInt("green", green); blue = color.getInt("blue", blue);
            }
            int rgb = (Math.max(0, Math.min(255, red)) << 16) | (Math.max(0, Math.min(255, green)) << 8) | Math.max(0, Math.min(255, blue));
            return new DustParticleEffect(rgb, (float) config.getDouble("size", 1d));
        }
        Identifier id = Identifier.tryParse(path.contains(":") ? path : "minecraft:" + path);
        if (id == null) throw new IllegalArgumentException("Invalid particle '" + raw + "'");
        ParticleType<?> type = Registries.PARTICLE_TYPE.get(id);
        if (type instanceof SimpleParticleType simple) return simple;
        throw new IllegalArgumentException("Particle '" + raw + "' requires data not supplied by this config");
    }
}
