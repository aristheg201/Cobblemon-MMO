package vn.svframe.svframelib.fabric;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframelib.fabric.runtime.script.ScriptContext;
import vn.svframe.svframelib.fabric.runtime.script.ScriptPlatform;
import vn.svframe.svframelib.fabric.runtime.script.Vector3;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.player.cooldown.CooldownInfo;

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

    @Override public boolean isLiving(UUID target) { return entity(target) instanceof LivingEntity; }
    @Override public boolean isOnFire(UUID target) { Entity found = entity(target); return found != null && found.getFireTicks() > 0; }

    @Override
    public boolean hasPermission(UUID target, String permission) {
        ServerPlayerEntity player = player(target);
        return player != null && SVFrameLibPermissionBridge.has(player, permission);
    }

    @Override public boolean isCreative(UUID target) { ServerPlayerEntity player = player(target); return player != null && player.getAbilities().creativeMode; }

    @Override
    public int foodLevel(UUID target) {
        ServerPlayerEntity player = requirePlayer(target, "food condition");
        return player.getHungerManager().getFoodLevel();
    }

    @Override
    public void setFoodLevel(UUID target, int value) {
        requirePlayer(target, "feed").getHungerManager().setFoodLevel(Math.max(0, Math.min(20, value)));
    }

    @Override
    public void setSaturation(UUID target, float value) {
        requirePlayer(target, "saturate").getHungerManager().setSaturationLevel(Math.max(0.0F, value));
    }

    @Override
    public String worldName(UUID reference) {
        Entity found = entity(reference);
        if (found == null) return "";
        return found.getWorld().getRegistryKey().getValue().toString();
    }

    @Override
    public String biome(UUID reference, Vector3 location) {
        Entity found = entity(reference);
        if (!(found != null && found.getWorld() instanceof ServerWorld world)) return "";
        BlockPos pos = BlockPos.ofFloored(location.x(), location.y(), location.z());
        return world.getBiome(pos).getKey().map(key -> key.getValue().toString()).orElse("");
    }

    @Override
    public long worldTime(UUID reference) {
        Entity found = entity(reference);
        if (found == null) return 0L;
        return Math.floorMod(found.getWorld().getTimeOfDay(), 24000L);
    }

    @Override
    public void damage(UUID target, double amount, String type) { damage(null, target, amount, type); }

    @Override
    public void damage(UUID source, UUID target, double amount, String type) {
        Entity victim = entity(target);
        if (!(victim instanceof LivingEntity living) || !Double.isFinite(amount) || amount <= 0) return;
        Entity attacker = entity(source);
        if (attacker instanceof ServerPlayerEntity player) {
            var types = new java.util.ArrayList<vn.svframe.svframelib.damage.DamageType>();
            for (String value : (type == null ? "" : type).split("[,;+\\s]+"))
                if (!value.isBlank()) types.add(vn.svframe.svframelib.damage.DamageType.valueOf(value.toUpperCase(Locale.ROOT)));
            if (types.isEmpty()) types.add(vn.svframe.svframelib.damage.DamageType.SKILL);
            var metadata = new vn.svframe.svframelib.player.PlayerMetadata(MMOPlayerData.setup(player).getStatMap(),
                    vn.svframe.svframelib.api.player.EquipmentSlot.MAIN_HAND);
            vn.svframe.svframelib.SVFrameLib.inst().getDamage().registerAttack(
                    new vn.svframe.svframelib.damage.AttackMetadata(
                            new vn.svframe.svframelib.damage.DamageMetadata(amount, types), living, metadata));
        } else {
            living.damage(attacker instanceof LivingEntity mob ? living.getDamageSources().mobAttack(mob)
                    : living.getDamageSources().generic(), (float) Math.min(Float.MAX_VALUE, amount));
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
        if (entity != null) spawnParticle((ServerWorld) entity.getWorld(),
                new Vector3(entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ()),
                particle, count, dx, dy, dz, speed);
    }

    @Override
    public void particleAt(Vector3 point, String particle, int count, double dx, double dy, double dz, double speed) {
        ServerWorld world = firstWorld();
        if (world != null) spawnParticle(world, point, particle, count, dx, dy, dz, speed);
    }

    @Override
    public void particleAt(UUID reference, Vector3 point, String particle, int count, double dx, double dy, double dz, double speed) {
        Entity caster = entity(reference);
        if (caster != null) spawnParticle((ServerWorld)caster.getWorld(), point, particle, count, dx, dy, dz, speed);
    }

    @Override
    public void displayModel(UUID reference, String model, Vector3 at, int ticks, boolean follow) {
        Entity caster=entity(reference);
        if (!NativeVisualRuntime.spawn(model,caster,new Vec3d(at.x(),at.y(),at.z()),ticks,follow)) throw new IllegalArgumentException("Unknown/unavailable native visual model: "+model);
    }

    @Override
    public void sound(UUID target, String sound, float volume, float pitch) {
        Entity entity = entity(target);
        if (entity == null) return;
        var id = identifier(sound);
        var event = Registries.SOUND_EVENT.containsId(id) ? Registries.SOUND_EVENT.get(id) : net.minecraft.sound.SoundEvent.of(id);
        ((ServerWorld) entity.getWorld()).playSound(null, entity.getX(), entity.getY(), entity.getZ(), event,
                SoundCategory.PLAYERS, volume, pitch);
    }

    @Override
    public void playerSound(UUID target, String sound, float volume, float pitch) {
        ServerPlayerEntity player = requirePlayer(target, "player_sound");
        player.playSound(Registries.SOUND_EVENT.get(identifier(sound)), volume, pitch);
    }

    @Override
    public void potion(UUID target, String effect, int level, int duration, boolean ambient, boolean particles, boolean icon) {
        Entity entity = entity(target);
        if (!(entity instanceof LivingEntity living)) return;
        var entry = Registries.STATUS_EFFECT.getEntry(identifier(effect))
                .orElseThrow(() -> new IllegalArgumentException("Unknown status effect: " + effect));
        living.addStatusEffect(new StatusEffectInstance(entry, Math.max(1, duration), Math.max(0, level), ambient, particles, icon));
    }

    @Override
    public void removePotion(UUID target, String effect) {
        Entity entity = entity(target);
        if (!(entity instanceof LivingEntity living)) return;
        var entry = Registries.STATUS_EFFECT.getEntry(identifier(effect))
                .orElseThrow(() -> new IllegalArgumentException("Unknown status effect: " + effect));
        living.removeStatusEffect(entry);
    }

    @Override public void velocity(UUID target, Vector3 vector) {
        Entity entity = entity(target);
        if (entity == null) return;
        entity.setVelocity(vector.x(), vector.y(), vector.z());
        entity.velocityModified = true;
        entity.velocityDirty = true;
        if (entity instanceof ServerPlayerEntity player)
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
    }
    @Override public void teleport(UUID target, Vector3 location) { Entity entity = entity(target); if (entity != null) entity.requestTeleport(location.x(), location.y(), location.z()); }

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
        for (LivingEntity living : ((ServerWorld) center.getWorld()).getEntitiesByClass(LivingEntity.class, box, Entity::isAlive))
            if (!living.getUuid().equals(target)) result.add(living.getUuid());
        return List.copyOf(result);
    }

    @Override public void setOnFire(UUID target, int ticks) { Entity entity = entity(target); if (entity != null) entity.setOnFireForTicks(Math.max(0, ticks)); }

    @Override
    public void noDamageTicks(UUID target, int ticks, boolean stack, boolean min, boolean max) {
        Entity entity = entity(target);
        if (!(entity instanceof LivingEntity living)) throw new IllegalArgumentException("set_no_damage_ticks requires a living target");
        int resolved = Math.max(0, ticks);
        if (stack) resolved += living.timeUntilRegen;
        else if (max) resolved = Math.max(resolved, living.timeUntilRegen);
        else if (min) resolved = Math.min(resolved, living.timeUntilRegen);
        living.timeUntilRegen = Math.max(0, resolved);
    }

    @Override
    public void actionBar(UUID target, String message, int priority, int duration) {
        ServerPlayerEntity player = requirePlayer(target, "action_bar");
        MMOPlayerData.setup(player).getActionBar().show(priority, Math.max(0, duration), message);
    }

    @Override public void message(UUID target, String message) { requirePlayer(target, "tell").sendMessage(Text.literal(message), false); }
    @Override public void kick(UUID target, String message) { requirePlayer(target, "kick").networkHandler.disconnect(Text.literal(message)); }
    @Override public void closeInventory(UUID target) { requirePlayer(target, "close_inventory").closeHandledScreen(); }

    @Override
    public void dispatchCommand(UUID target, String command, boolean fromConsole, boolean operator) {
        MinecraftServer server = SVFrameLibFabricMod.server();
        if (server == null) throw new IllegalStateException("Server is not running");
        String clean = command == null ? "" : command.trim();
        if (clean.startsWith("/")) clean = clean.substring(1);
        if (clean.isBlank()) throw new IllegalArgumentException("Command cannot be blank");
        ServerCommandSource source = fromConsole ? server.getCommandSource() : requirePlayer(target, "dispatch_command").getCommandSource();
        if (operator) source = source.withLevel(4);
        server.getCommandManager().executeWithPrefix(source, clean);
    }

    @Override
    public void lightning(UUID reference, Vector3 location, boolean cosmetic) {
        Entity anchor = entity(reference);
        ServerWorld world = anchor != null && anchor.getWorld() instanceof ServerWorld sw ? sw : firstWorld();
        if (world == null) return;
        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        lightning.requestTeleport(location.x(), location.y(), location.z());
        lightning.setCosmetic(cosmetic);
        world.spawnEntity(lightning);
    }

    @Override
    public void giveItem(UUID target, String itemName, int amount) {
        ServerPlayerEntity player = requirePlayer(target, "give_item");
        Item item = Registries.ITEM.get(identifier(itemName));
        ItemStack stack = new ItemStack(item, Math.max(1, amount));
        player.getInventory().insertStack(stack);
        if (!stack.isEmpty()) player.dropItem(stack, false);
    }

    @Override
    public void trigger(UUID source, String script, ScriptContext context) {
        if (script == null || script.isBlank()) throw new IllegalArgumentException("Trigger script cannot be blank");
        SVFrameLibFabricMod.castScript(script, source, context.target(), context.objects());
    }

    @Override
    public void entityEffect(UUID target, String effect) {
        Entity entity = entity(target);
        if (entity == null) return;
        String normalized = effect == null ? "" : effect.trim().toUpperCase(Locale.ROOT);
        byte status = switch (normalized) {
            case "HURT" -> (byte) 2;
            case "DEATH" -> EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES;
            case "THORNS", "HURT_THORNS", "DROWN", "DROWNING", "HURT_DROWNED", "BURN", "FIRE", "HURT_FIRE" -> (byte) 2;
            case "TOTEM", "TOTEM_RESURRECT", "TOTEM_OF_UNDYING" -> EntityStatuses.USE_TOTEM_OF_UNDYING;
            default -> parseEntityStatus(normalized);
        };
        entity.getWorld().sendEntityStatus(entity, status);
    }

    @Override
    public boolean hasItem(UUID target, String itemName, int amount) {
        ServerPlayerEntity player = player(target);
        if (player == null) return false;
        Item item = Registries.ITEM.get(identifier(itemName));
        int left = Math.max(1, amount);
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(item) && (left -= stack.getCount()) <= 0) return true;
        }
        return false;
    }

    @Override
    public boolean takeItem(UUID target, String itemName, int amount) {
        ServerPlayerEntity player = player(target);
        if (player == null) return false;
        int wanted = Math.max(1, amount);
        if (!hasItem(target, itemName, wanted)) return false;
        Item item = Registries.ITEM.get(identifier(itemName));
        int left = wanted;
        for (int slot = 0; slot < player.getInventory().size() && left > 0; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isOf(item)) continue;
            int remove = Math.min(left, stack.getCount());
            stack.decrement(remove);
            left -= remove;
        }
        return left == 0;
    }

    @Override
    public boolean heldBooleanTag(UUID target, String tag) {
        if (tag == null || tag.isBlank()) return false;
        ServerPlayerEntity player = player(target);
        if (player == null) return false;
        NbtComponent data = player.getMainHandStack().get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.contains(tag) && data.copyNbt().getBoolean(tag);
    }

    @Override public boolean hasAmmo(UUID target, int amount) { return hasItem(target, "minecraft:arrow", amount); }
    @Override public boolean takeAmmo(UUID target, int amount) { return takeItem(target, "minecraft:arrow", amount); }

    @Override
    public boolean cooldownReady(UUID target, String path) {
        return !MMOPlayerData.setup(requirePlayer(target, "cooldown")).getCooldownMap().isOnCooldown(path);
    }

    @Override
    public void applyCooldown(UUID target, String path, double seconds) {
        MMOPlayerData.setup(requirePlayer(target, "apply_cooldown")).getCooldownMap().applyCooldown(path, Math.max(0.0d, seconds));
    }

    @Override
    public void reduceCooldown(UUID target, String path, String reduction, double value) {
        CooldownInfo info = MMOPlayerData.setup(requirePlayer(target, "reduce_cooldown")).getCooldownMap().getInfo(path);
        if (info == null || info.hasEnded()) return;
        switch (reduction == null ? "FLAT" : reduction.trim().toUpperCase(Locale.ROOT)) {
            case "FLAT" -> info.reduceFlat(value);
            case "INITIAL" -> info.reduceInitialCooldown(value);
            case "REMAINING" -> info.reduceRemainingCooldown(value);
            default -> throw new IllegalArgumentException("Unknown cooldown reduction type: " + reduction);
        }
    }

    @Override
    public void addStat(UUID target, String stat, String key, double value, boolean relative, boolean unique, long lifetimeTicks) {
        requirePlayer(target, "add_stat_modifier");
        UUID id = unique ? UUID.nameUUIDFromBytes(key.getBytes()) : UUID.randomUUID();
        NativeStatEngine.ModifierType type = relative ? NativeStatEngine.ModifierType.RELATIVE : NativeStatEngine.ModifierType.FLAT;
        long expires = lifetimeTicks <= 0 ? Long.MAX_VALUE :
                (lifetimeTicks > Long.MAX_VALUE - SVFrameLibFabricMod.currentTick() ? Long.MAX_VALUE : SVFrameLibFabricMod.currentTick() + lifetimeTicks);
        SVFrameLibStatMod.engine().register(target, stat, new NativeStatEngine.Modifier(id, key, value, type,
                NativeStatEngine.EquipmentSlot.OTHER, NativeStatEngine.ModifierSource.OTHER, expires));
    }

    @Override
    public void removeStat(UUID target, String stat, String key) {
        requirePlayer(target, "remove_stat_modifier");
        SVFrameLibStatMod.engine().removeByKey(target, stat, key);
    }

    @Override
    public void shootArrow(UUID target, double speed, double damage) {
        ServerPlayerEntity player = requirePlayer(target, "shoot_arrow");
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
        if (!(sourceEntity instanceof LivingEntity owner)) throw new IllegalArgumentException("shulker_bullet requires a living caster");
        if (targetEntity == null) throw new IllegalArgumentException("shulker_bullet requires a target");
        ServerWorld world = (ServerWorld) owner.getWorld();
        world.spawnEntity(new ScriptShulkerBulletEntity(world, owner, targetEntity, Math.max(0, damage)));
    }

    @Override public void delay(int ticks, Runnable runnable) { SVFrameLibFabricMod.schedule(Math.max(0, ticks), runnable); }

    @Override
    public void projectile(ProjectileSpec spec, Consumer<Vector3> tick, Consumer<UUID> hit, Runnable end) {
        fly(null, firstWorld(), spec, tick, hit, end);
    }

    @Override
    public void projectile(UUID reference, ProjectileSpec spec, Consumer<Vector3> tick, Consumer<UUID> hit, Runnable end) {
        Entity owner = entity(reference);
        if (owner != null) fly(owner, (ServerWorld)owner.getWorld(), spec, tick, hit, end);
    }

    private void fly(Entity owner, ServerWorld world, ProjectileSpec spec, Consumer<Vector3> tick, Consumer<UUID> hit, Runnable end) {
        if (world == null) return;
        Vector3 direction = spec.direction().normalize();
        int life = Math.max(1, Math.min(1200, spec.lifeTicks()));
        double speed = Math.max(0.01, Math.min(8, spec.speed()));
        double range = Math.max(0, Math.min(256, spec.range()));
        double size = Math.max(0, Math.min(8, spec.size()));
        if (!Double.isFinite(speed) || !Double.isFinite(range) || !Double.isFinite(size)) throw new IllegalArgumentException("Non-finite projectile parameters");
        class Flight implements Runnable {
            private Vector3 point = spec.origin();
            private int age;
            private double distance;
            @Override public void run() {
                if (owner != null && (!owner.isAlive() || owner.isRemoved() || owner.getWorld() != world)) return;
                if (age++ >= life || distance >= range) { end.run(); return; }
                Vector3 next = point.add(direction.multiply(Math.min(speed, range-distance)));
                Vec3d from = new Vec3d(point.x(),point.y(),point.z()), to = new Vec3d(next.x(),next.y(),next.z());
                if (!world.isChunkLoaded(BlockPos.ofFloored(to))) { end.run(); return; }
                var block = world.raycast(new net.minecraft.world.RaycastContext(from,to,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,owner));
                double nearest = block.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? Double.POSITIVE_INFINITY : from.squaredDistanceTo(block.getPos());
                LivingEntity collision = null;
                Box search = new Box(from,to).expand(size);
                for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class,search,e -> e.isAlive() && e != owner && !e.isSpectator())) {
                    var intersection = candidate.getBoundingBox().expand(size).raycast(from,to);
                    double at = candidate.getBoundingBox().expand(size).contains(from) ? 0 : intersection.map(from::squaredDistanceTo).orElse(Double.POSITIVE_INFINITY);
                    if (at < nearest) { nearest=at; collision=candidate; }
                }
                if (Double.isFinite(nearest)) {
                    Vec3d contact = from.add(to.subtract(from).normalize().multiply(Math.sqrt(nearest)));
                    tick.accept(new Vector3(contact.x,contact.y,contact.z));
                    if (collision != null) hit.accept(collision.getUuid());
                    end.run(); return;
                }
                point=next; distance+=speed; tick.accept(point);
                SVFrameLibFabricMod.schedule(1,this);
            }
        }
        SVFrameLibFabricMod.schedule(1,new Flight());
    }

    private static void spawnParticle(ServerWorld world, Vector3 point, String name, int count, double dx, double dy, double dz, double speed) {
        ParticleEffect effect = NativeParticles.effect(name, 0xa83cff, 1, "minecraft:stone");
        world.spawnParticles(effect, point.x(), point.y(), point.z(), Math.max(0, Math.min(4096,count)), dx, dy, dz, speed);
    }

    private static ServerPlayerEntity player(UUID uuid) {
        MinecraftServer server = SVFrameLibFabricMod.server();
        return server == null || uuid == null ? null : server.getPlayerManager().getPlayer(uuid);
    }

    private static ServerPlayerEntity requirePlayer(UUID uuid, String mechanic) {
        ServerPlayerEntity player = player(uuid);
        if (player == null) throw new IllegalArgumentException(mechanic + " requires an online player");
        return player;
    }

    private static Entity entity(UUID uuid) {
        if (uuid == null) return null;
        MinecraftServer server = SVFrameLibFabricMod.server();
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
        MinecraftServer server = SVFrameLibFabricMod.server();
        return server == null ? null : server.getOverworld();
    }

    private static Identifier identifier(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) throw new IllegalArgumentException("Registry identifier cannot be blank");
        if (!value.contains(":")) value = "minecraft:" + value;
        return Identifier.of(value);
    }

    private static byte parseEntityStatus(String raw) {
        try { return Byte.parseByte(raw); }
        catch (NumberFormatException ignored) { throw new IllegalArgumentException("Unsupported entity effect: " + raw); }
    }

    private static final class ScriptShulkerBulletEntity extends ShulkerBulletEntity {
        private final LivingEntity scriptOwner;
        private final double scriptDamage;
        private ScriptShulkerBulletEntity(ServerWorld world, LivingEntity owner, Entity target, double damage) {
            super(world, owner, target, Direction.Axis.Y);
            this.scriptOwner = owner;
            this.scriptDamage = damage;
        }
        @Override protected void onEntityHit(EntityHitResult hitResult) {
            Entity hit = hitResult.getEntity();
            if (hit instanceof LivingEntity living && scriptDamage > 0)
                living.damage(living.getDamageSources().mobProjectile(this, scriptOwner), (float) scriptDamage);
            discard();
        }
    }
}
