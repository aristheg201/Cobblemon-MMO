package vn.svframe.svframelib.player.particle;

import vn.svframe.svframelib.util.configobject.ConfigObject;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.Objects;

/** Native particle descriptor preserving the display API used by SVFrameLib particle effects. */
public class ParticleInformation {
    private final ParticleEffect particle;
    private final int amount;
    private final double xOffset, yOffset, zOffset, speed;

    public ParticleInformation(ParticleEffect particle) { this(particle, 1, 0f, 0d, 0d, 0d); }
    public ParticleInformation(ParticleEffect particle, int amount, float speed, double offset, Object ignoredData) { this(particle, amount, speed, offset, offset, offset); }
    public ParticleInformation(ParticleEffect particle, int amount, float speed, double xOffset, double yOffset, double zOffset, Object ignoredData) { this(particle, amount, speed, xOffset, yOffset, zOffset); }
    public ParticleInformation(ParticleEffect particle, int amount, double speed, double xOffset, double yOffset, double zOffset) {
        this.particle = Objects.requireNonNull(particle, "particle");
        this.amount = Math.max(0, amount);
        this.speed = speed;
        this.xOffset = xOffset; this.yOffset = yOffset; this.zOffset = zOffset;
    }

    public ParticleEffect particle() { return particle; }
    public void display(ServerWorld world, Vec3d pos) { display(world, pos, amount, xOffset, yOffset, zOffset, speed); }
    public void display(ServerWorld world, Vec3d pos, double speed) { display(world, pos, amount, xOffset, yOffset, zOffset, speed); }
    public void display(ServerWorld world, Vec3d pos, int amount, double x, double y, double z, double speed) { world.spawnParticles(particle, pos.x, pos.y, pos.z, Math.max(0, amount), x, y, z, speed); }

    public static ParticleInformation fromConfig(Object raw) {
        if (raw instanceof ParticleInformation info) return info;
        if (raw instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            raw = new vn.svframe.svframelib.util.configobject.MapConfigObject("particle", values);
        }
        if (!(raw instanceof ConfigObject obj)) throw new IllegalArgumentException("Particle config must be a ConfigObject");
        String name = obj.getString("particle", obj.getString("name", "FLAME"));
        int amount = obj.getInt("amount", 1);
        double offset = obj.getDouble("offset", obj.getDouble("r-offset", 0d));
        double x = obj.getDouble("x-offset", offset), y = obj.getDouble("y-offset", offset), z = obj.getDouble("z-offset", offset);
        double speed = obj.getDouble("speed", 0d);
        return new ParticleInformation(resolve(name, obj), amount, speed, x, y, z);
    }

    public static ParticleInformation of(ParticleEffect particle) { return new ParticleInformation(particle); }

    private static ParticleEffect resolve(String raw, ConfigObject config) {
        int rgb=0xff0000;
        Object rawColor=config.get("color");
        if(rawColor instanceof Map<?,?> || rawColor instanceof ConfigObject){
            ConfigObject color=config.getObject("color");
            int red=Math.max(0,Math.min(255,color.getInt("red",255)));
            int green=Math.max(0,Math.min(255,color.getInt("green",0)));
            int blue=Math.max(0,Math.min(255,color.getInt("blue",0)));
            rgb=(red<<16)|(green<<8)|blue;
        } else if(rawColor instanceof String hex)rgb=Integer.parseInt(hex.replace("#",""),16);
        return vn.svframe.svframelib.fabric.NativeParticles.effect(raw,rgb,(float)config.getDouble("size",1),config.getString("material","minecraft:stone"));
    }
}
