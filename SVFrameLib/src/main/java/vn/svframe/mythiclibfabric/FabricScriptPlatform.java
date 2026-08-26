package vn.svframe.mythiclibfabric;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import vn.svframe.mythiclibfabric.runtime.script.ScriptContext;
import vn.svframe.mythiclibfabric.runtime.script.ScriptPlatform;
import vn.svframe.mythiclibfabric.runtime.script.Vector3;
import vn.svframe.svframelib.api.player.MMOPlayerData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

final class FabricScriptPlatform implements ScriptPlatform {
    @Override
    public boolean canTarget(UUID source, UUID target, String mode) {
        Entity entity = entity(target);
        if (!(entity instanceof LivingEntity living) || !living.isAlive()) return false;
        return source == null || !source.equals(target) || mode == null || !mode.toLowerCase(Locale.ROOT).contains("other");
    }

    @Override
    public void damage(UUID target, double amount, String type) {
        Entity entity = entity(target);
        if (entity instanceof LivingEntity living && amount > 0) {
            living.damage(living.getDamageSources().generic(), (float) amount);
        }
    }

    @Override
    public void heal(UUID target, double amount) {
        Entity entity = entity(target);
        if (entity instanceof LivingEntity living && amount > 0) living.heal((float) amount);
    }

    @Override
    public void particle(UUID target, String particle, int count, double dx, double dy, double dz, double speed) {
        Entity entity = entity(target);
        if (entity != null) {
            spawnParticle((ServerWorld) entity.getWorld(),
                    new Vector3(entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ()),
                    particle, count, dx, dy, dz, speed);
        }
    }

    @Override
    public void particleAt(Vector3 point, String particle, int count, double dx, double dy, double dz, double speed) {
        ServerWorld world = firstWorld();
        if (world != null) spawnParticle(world, point, particle, count, dx, dy, dz, speed);
    }

    @Override
    public void sound(UUID target, String sound, float volume, float pitch) {
        Entity entity = entity(target);
        if (entity == null) return;
        var event = Registries.SOUND_EVENT.get(identifier(sound));
        ((ServerWorld) entity.getWorld()).playSound(null, entity.getX(), entity.getY(), entity.getZ(), event,
                SoundCategory.PLAYERS, volume, pitch);
    }

    @Override
    public void potion(UUID target, String effect, int level, int duration, boolean ambient, boolean particles, boolean icon) {
        Entity entity = entity(target);
        if (!(entity instanceof LivingEntity living)) return;
        var entry = Registries.STATUS_EFFECT.getEntry(identifier(effect))
                .orElseThrow(() -> new IllegalArgumentException("Unknown status effect: " + effect));
        living.addStatusEffect(new StatusEffectInstance(entry, Math.max(1, duration), Math.max(0, level),
                ambient, particles, icon));
    }

    @Override
    public void removePotion(UUID target, String effect) {
        Entity entity = entity(target);
        if (!(entity instanceof LivingEntity living)) return;
        var entry = Registries.STATUS_EFFECT.getEntry(identifier(effect))
                .orElseThrow(() -> new IllegalArgumentException("Unknown status effect: " + effect));
        living.removeStatusEffect(entry);
    }

    @Override
    public void velocity(UUID target, Vector3 vector) {
        Entity entity = entity(target);
        if (entity != null) entity.setVelocity(vector.x(), vector.y(), vector.z());
    }

    @Override
    public Vector3 location(UUID target) {
        Entity entity = entity(target);
        return entity == null ? new Vector3(0, 0, 0) : new Vector3(entity.getX(), entity.getY(), entity.getZ());
    }

    @Override
    public Vector3 eyeDirection(UUID target) {
        Entity entity = entity(target);
        if (entity == null) return new Vector3(0, 0, 1);
        Vec3d vec = entity.getRotationVec(1.0F);
        return new Vector3(vec.x, vec.y, vec.z);
    }

    @Override
    public Collection<UUID> nearby(UUID target, double horizontal, double vertical) {
        Entity center = entity(target);
        if (center == null) return List.of();
        Box box = center.getBoundingBox().expand(horizontal, vertical, horizontal);
        List<UUID> result = new ArrayList<>();
        for (LivingEntity living : ((ServerWorld) center.getWorld()).getEntitiesByClass(LivingEntity.class, box, Entity::isAlive)) {
            if (!living.getUuid().equals(target)) result.add(living.getUuid());
        }
        return List.copyOf(result);
    }

    @Override
    public void setOnFire(UUID target, int ticks) {
        Entity entity = entity(target);
        if (entity != null) entity.setOnFireForTicks(Math.max(0, ticks));
    }

    @Override
    public void noDamageTicks(UUID target, int ticks, boolean stack, boolean min, boolean max) {
        Entity entity = entity(target);
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalArgumentException("set_no_damage_ticks requires a living target");
        }
        int resolved = Math.max(0, ticks);
        if (stack) resolved += living.timeUntilRegen;
        else if (max) resolved = Math.max(resolved, living.timeUntilRegen);
        else if (min) resolved = Math.min(resolved, living.timeUntilRegen);
        living.timeUntilRegen = Math.max(0, resolved);
    }

    @Override
    public void actionBar(UUID target, String message, int priority, int duration) {
        Entity entity = entity(target);
        if (!(entity instanceof ServerPlayerEntity player)) return;
        MMOPlayerData.setup(player).getActionBar().show(priority, Math.max(0, duration), message);
    }

    @Override
    public void trigger(UUID source, String script, ScriptContext context) {
        if (script == null || script.isBlank()) throw new IllegalArgumentException("Trigger script cannot be blank");
        MythicLibFabricMod.castScript(script, source, context.target(), context.objects());
    }

    @Override
    public void entityEffect(UUID target, String effect) {
        Entity entity = entity(target);
        if (entity == null) return;
        String normalized = effect == null ? "" : effect.trim().toUpperCase(Locale.ROOT);
        byte status = switch (normalized) {
            case "HURT" -> (byte) 2;
            case "DEATH" -> EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES;
            case "THORNS", "HURT_THORNS" -> (byte) 2;
            case "DROWN", "DROWNING", "HURT_DROWNED" -> (byte) 2;
            case "BURN", "FIRE", "HURT_FIRE" -> (byte) 2;
            case "TOTEM", "TOTEM_RESURRECT", "TOTEM_OF_UNDYING" -> EntityStatuses.USE_TOTEM_OF_UNDYING;
            default -> parseEntityStatus(normalized);
        };
        entity.getWorld().sendEntityStatus(entity, status);
    }

    @Override
    public boolean hasAmmo(UUID target, int amount) {
        Entity entity = entity(target);
        if (!(entity instanceof ServerPlayerEntity player)) return false;
        int left = Math.max(1, amount);
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(Items.ARROW) && (left -= stack.getCount()) <= 0) return true;
        }
        return false;
    }

    @Override
    public boolean takeAmmo(UUID target, int amount) {
        Entity entity = entity(target);
        if (!(entity instanceof ServerPlayerEntity player)) return false;
        int wanted = Math.max(1, amount);
        if (!hasAmmo(target, wanted)) return false;
        int left = wanted;
        for (int slot = 0; slot < player.getInventory().size() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(Items.ARROW)) continue;
            int remove = Math.min(left, stack.getCount());
            stack.decrement(remove);
            left -= remove;
        }
        return left == 0;
    }

    @Override
    public void shootArrow(UUID target, double speed, double damage) {
        Entity entity = entity(target);
        if (!(entity instanceof ServerPlayerEntity player)) {
            throw new IllegalArgumentException("shoot_arrow requires a player caster");
        }
        ServerWorld world = (ServerWorld) player.getWorld();
        ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), null);
        Vec3d direction = player.getRotationVec(1.0F).normalize().multiply(Math.max(0.01, speed));
        arrow.setVelocity(direction);
        arrow.setDamage(Math.max(0, damage));
        world.spawnEntity(arrow);
    }

    @Override
    public void shulkerBullet(UUID source, UUID target, double damage) {
        Entity sourceEntity = entity(source);
        Entity targetEntity = entity(target);
        if (!(sourceEntity instanceof LivingEntity owner)) {
            throw new IllegalArgumentException("shulker_bullet requires a living caster");
        }
        if (targetEntity == null) throw new IllegalArgumentException("shulker_bullet requires a target");
        ServerWorld world = (ServerWorld) owner.getWorld();
        world.spawnEntity(new ScriptShulkerBulletEntity(world, owner, targetEntity, Math.max(0, damage)));
    }

    @Override
    public void delay(int ticks, Runnable runnable) {
        MythicLibFabricMod.schedule(Math.max(0, ticks), runnable);
    }

    @Override
    public void projectile(ProjectileSpec spec, Consumer<Vector3> tick, Consumer<UUID> hit, Runnable end) {
        Vector3 direction = spec.direction().normalize();
        int life = Math.max(1, spec.lifeTicks());
        double speed = Math.max(0.01, spec.speed());
        class Flight implements Runnable {
            private Vector3 point = spec.origin();
            private int age;
            private double distance;

            @Override
            public void run() {
                if (age++ >= life || distance >= spec.range()) {
                    end.run();
                    return;
                }
                point = point.add(direction.multiply(speed));
                distance += speed;
                tick.accept(point);
                ServerWorld world = firstWorld();
                if (world != null) {
                    Box box = new Box(point.x() - spec.size(), point.y() - spec.size(), point.z() - spec.size(),
                            point.x() + spec.size(), point.y() + spec.size(), point.z() + spec.size());
                    List<LivingEntity> entities = world.getEntitiesByClass(LivingEntity.class, box, Entity::isAlive);
                    if (!entities.isEmpty()) {
                        hit.accept(entities.getFirst().getUuid());
                        end.run();
                        return;
                    }
                }
                MythicLibFabricMod.schedule(1, this);
            }
        }
        MythicLibFabricMod.schedule(1, new Flight());
    }

    private static void spawnParticle(ServerWorld world, Vector3 point, String name, int count,
                                      double dx, double dy, double dz, double speed) {
        Object value = Registries.PARTICLE_TYPE.get(identifier(name));
        if (value instanceof ParticleEffect effect) {
            world.spawnParticles(effect, point.x(), point.y(), point.z(), Math.max(1, count), dx, dy, dz, speed);
            return;
        }
        throw new IllegalArgumentException("Particle type is not spawnable as a ParticleEffect: " + name);
    }

    private static Entity entity(UUID uuid) {
        if (uuid == null) return null;
        MinecraftServer server = MythicLibFabricMod.server();
        if (server == null) return null;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) return player;
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static ServerWorld firstWorld() {
        MinecraftServer server = MythicLibFabricMod.server();
        return server == null ? null : server.getOverworld();
    }

    private static Identifier identifier(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) throw new IllegalArgumentException("Registry identifier cannot be blank");
        if (!value.contains(":")) value = "minecraft:" + value;
        return Identifier.of(value);
    }

    private static byte parseEntityStatus(String raw) {
        try {
            return Byte.parseByte(raw);
        } catch (NumberFormatException ignored) {
            throw new IllegalArgumentException("Unsupported entity effect: " + raw);
        }
    }

    private static final class ScriptShulkerBulletEntity extends ShulkerBulletEntity {
        private final LivingEntity scriptOwner;
        private final double scriptDamage;

        private ScriptShulkerBulletEntity(ServerWorld world, LivingEntity owner, Entity target, double damage) {
            super(world, owner, target, Direction.Axis.Y);
            this.scriptOwner = owner;
            this.scriptDamage = damage;
        }

        @Override
        protected void onEntityHit(EntityHitResult hitResult) {
            Entity hit = hitResult.getEntity();
            if (hit instanceof LivingEntity living && scriptDamage > 0) {
                living.damage(living.getDamageSources().mobProjectile(this, scriptOwner), (float) scriptDamage);
            }
            discard();
        }
    }
}
