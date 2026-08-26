package vn.svframe.mythiclibfabric.runtime.script;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Native runtime boundary used by the script engine.
 *
 * <p>There are intentionally no default implementations here. A missing Fabric
 * operation must be a compile-time error instead of silently degrading a
 * MythicLib mechanic into a no-op.</p>
 */
public interface ScriptPlatform {
    record ProjectileSpec(Vector3 origin, Vector3 direction, double speed, double range, double size, int lifeTicks) {}

    boolean canTarget(UUID caster, UUID target, String interaction);
    void damage(UUID target, double amount, String type);
    void heal(UUID target, double amount);
    void particle(UUID target, String particle, int amount, double x, double y, double z, double speed);
    void particleAt(Vector3 at, String particle, int amount, double x, double y, double z, double speed);
    void sound(UUID target, String sound, float volume, float pitch);
    void potion(UUID target, String effect, int level, int duration, boolean ambient, boolean particles, boolean icon);
    void removePotion(UUID target, String effect);
    void velocity(UUID target, Vector3 velocity);
    Vector3 location(UUID target);
    Vector3 eyeDirection(UUID target);
    Collection<UUID> nearby(UUID source, double radius, double height);
    void setOnFire(UUID target, int ticks);
    void noDamageTicks(UUID target, int ticks, boolean stack, boolean min, boolean max);
    void actionBar(UUID target, String message, int priority, int duration);
    void trigger(UUID caster, String trigger, ScriptContext context);
    void entityEffect(UUID target, String effect);
    boolean takeAmmo(UUID player, int amount);
    boolean hasAmmo(UUID player, int amount);
    void shootArrow(UUID player, double speed, double damage);
    void shulkerBullet(UUID source, UUID target, double damage);
    void delay(int ticks, Runnable runnable);
    void projectile(ProjectileSpec spec, Consumer<Vector3> tick, Consumer<UUID> hitEntity, Runnable hitBlock);
}
