package vn.svframe.svframelib.fabric;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.damage.AttackMetadata;
import vn.svframe.svframelib.damage.DamageMetadata;
import vn.svframe.svframelib.damage.DamageType;
import vn.svframe.svframelib.player.PlayerMetadata;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Galio-inspired launch, camera aim, committed descent and a single attributed impact. Server thread only. */
public final class HeroEntranceRuntime {
    public enum Phase { WARMUP, LAUNCH, AIM, DESCENT }
    public record View(Phase phase, Vec3d target, int age) { }
    private static final int LAUNCH_TICKS = 12;
    private static final Map<UUID, Cast> CASTS = new HashMap<>();
    private static final Map<ServerPlayerEntity, Long> LANDING_GRACE = new HashMap<>();
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1f, .78f, .15f), 1.3f);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(1f, .15f, .15f), 1.3f);
    private static long tick;

    private HeroEntranceRuntime() { }

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(HeroEntranceRuntime::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.player));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    public static boolean canStart(ServerPlayerEntity player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isDisconnected()
                && player.isOnGround() && !player.hasVehicle() && !player.isSleeping()
                && !player.isTouchingWater() && !player.isInLava() && !CASTS.containsKey(player.getUuid())
                && safe(player, player.getPos());
    }

    public static boolean start(ServerPlayerEntity player, Map<String, ?> parameters) {
        if (!canStart(player)) return false;
        HeroEntranceSettings settings = HeroEntranceSettings.from(parameters);
        Vec3d apex = player.getPos().add(0, settings.height(), 0);
        ServerWorld world = player.getServerWorld();
        if (apex.y + player.getHeight() >= world.getTopY()
                || world.raycast(new RaycastContext(player.getEyePos(), apex.add(0, player.getStandingEyeHeight(), 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player)).getType() != HitResult.Type.MISS)
            return false;
        Cast cast = new Cast(player, settings, apex);
        cast.cursedExecution=Boolean.TRUE.equals(parameters.get("cursed_execution"));
        cast.deathSentence=Boolean.TRUE.equals(parameters.get("death_sentence"));
        if (settings.warmupTicks()>0) cast.phase=Phase.WARMUP;
        CASTS.put(player.getUuid(), cast);
        player.setNoGravity(settings.warmupTicks()==0 || cast.previousNoGravity);
        player.fallDistance = 0;
        if (settings.warmupTicks()==0) velocity(player, new Vec3d(0, settings.height() / settings.launchTicks(), 0));
        player.sendMessage(Text.translatable(cast.deathSentence?"skill.svframelib.death_sentence.aim":"skill.svframelib.hero_entrance.aim"), true);
        if (cast.deathSentence) DeathSentencePresentation.started(player);
        else world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1, .6f);
        return true;
    }

    public static View view(ServerPlayerEntity player) {
        Cast cast = CASTS.get(player.getUuid());
        return cast == null || cast.player != player ? null : new View(cast.phase, cast.target, cast.age);
    }

    public static boolean protectsFall(ServerPlayerEntity player) {
        Cast cast = CASTS.get(player.getUuid());
        return (cast != null && cast.player == player) || LANDING_GRACE.getOrDefault(player, -1L) >= tick;
    }

    public static boolean confirm(ServerPlayerEntity player) {
        Cast cast = CASTS.get(player.getUuid());
        if (cast == null || cast.player != player || cast.phase != Phase.AIM || cast.target == null
                || !safe(player, cast.target)) return false;
        if(cast.deathSentence&&cast.age<cast.settings.warmupTicks()+cast.settings.launchTicks()+cast.settings.aimTicks())return false;
        cast.phase = Phase.DESCENT;
        cast.descentStart = player.getPos();
        cast.descentAge = 0;
        if (cast.deathSentence) DeathSentencePresentation.descending(player);
        else cast.world.playSound(null, BlockPos.ofFloored(cast.target), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, .6f, 1.8f);
        return true;
    }

    public static boolean cancel(ServerPlayerEntity player) {
        Cast cast = CASTS.get(player.getUuid());
        if (cast == null || cast.player != player) return false;
        CASTS.remove(player.getUuid());
        restore(cast);
        return true;
    }

    public static void clear() {
        CASTS.values().forEach(HeroEntranceRuntime::restore);
        CASTS.clear();
        LANDING_GRACE.clear();
    }

    private static void tick(MinecraftServer server) {
        tick++;
        LANDING_GRACE.entrySet().removeIf(entry -> entry.getValue() < tick);
        // Snapshot because impact damage may synchronously invoke another skill.
        for (Cast cast : CASTS.values().toArray(Cast[]::new)) {
            ServerPlayerEntity player = cast.player;
            if (!player.isAlive() || player.isDisconnected() || player.isSpectator() || player.hasVehicle()
                    || player.getServerWorld() != cast.world
                    || server.getPlayerManager().getPlayer(player.getUuid()) != player) {
                cancel(player);
                continue;
            }
            try { advance(cast); }
            catch (RuntimeException failure) {
                cancel(player);
                java.util.logging.Logger.getLogger("SVFrameLib-HeroEntrance").log(
                        java.util.logging.Level.SEVERE, "Cancelled failed Hero's Entrance", failure);
            }
        }
    }

    private static void advance(Cast cast) {
        ServerPlayerEntity player = cast.player;
        player.fallDistance = 0;
        cast.age++;
        if(cast.deathSentence) DeathSentencePresentation.tick(player,cast.age);
        if(cast.phase==Phase.WARMUP) {
            if(cast.age<cast.settings.warmupTicks())return;
            cast.origin=player.getPos();cast.apex=cast.origin.add(0,cast.settings.height(),0);
            if(cast.world.raycast(new RaycastContext(player.getEyePos(),cast.apex.add(0,player.getStandingEyeHeight(),0),
                    RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player)).getType()!=HitResult.Type.MISS){cancel(player);return;}
            player.setNoGravity(true);cast.phase=Phase.LAUNCH;
            if(cast.deathSentence)DeathSentencePresentation.launched(player,cast.origin);
        }
        if (cast.phase == Phase.LAUNCH) {
            int launchAge=cast.age-cast.settings.warmupTicks();
            Vec3d next = cast.origin.lerp(cast.apex, Math.min(1d, launchAge / (double) cast.settings.launchTicks()));
            if (!clearPath(player, next)) { cancel(player); return; }
            move(player, next);
            if (launchAge >= cast.settings.launchTicks()) {
                cast.phase = Phase.AIM;
                if(cast.deathSentence)DeathSentencePresentation.hovering(player);
            }
        }
        if (cast.phase == Phase.AIM) {
            move(player, cast.apex);
            cast.target = aimedGround(cast);
            if ((cast.age & 1) == 0) ring(cast, cast.target == null ? cast.origin : cast.target,
                    cast.target == null ? RED : cast.deathSentence?DeathSentencePresentation.TARGET:GOLD);
            int endAim=cast.settings.warmupTicks()+cast.settings.launchTicks()+cast.settings.aimTicks();
            if ((!cast.deathSentence&&player.isSneaking()) || cast.age >= endAim) {
                if (!confirm(player) && cast.age >= endAim) cancel(player);
            }
        } else if (cast.phase == Phase.DESCENT) {
            if (!safe(player, cast.target)) { cancel(player); return; }
            cast.descentAge++;
            double fraction = Math.min(1d, cast.descentAge / (double) cast.settings.descentTicks());
            Vec3d next = cast.descentStart.lerp(cast.target, fraction * fraction);
            if (!clearPath(player, next)) { cancel(player); return; }
            move(player, next);
            if ((cast.age & 1) == 0) ring(cast, cast.target, cast.deathSentence?DeathSentencePresentation.TARGET:GOLD);
            if(!cast.deathSentence) particles(cast, ParticleTypes.END_ROD, player.getPos().add(0, 1, 0));
            if (fraction >= 1d) {
                CASTS.remove(player.getUuid()); // Exactly one impact, including reentrant callbacks.
                restore(cast);
                impact(cast);
            }
        }
    }

    private static Vec3d aimedGround(Cast cast) {
        ServerPlayerEntity player = cast.player;
        Vec3d eye = player.getEyePos(), direction = player.getRotationVec(1f);
        double reach = Math.hypot(cast.settings.range(), cast.settings.height() + 16);
        var hit = cast.world.raycast(new RaycastContext(eye, eye.add(direction.multiply(reach)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d projected = hit.getType() == HitResult.Type.MISS ? eye.add(direction.multiply(reach)) : hit.getPos();
        double dx = projected.x - cast.origin.x, dz = projected.z - cast.origin.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal > cast.settings.range()) {
            double scale = cast.settings.range() / horizontal;
            projected = new Vec3d(cast.origin.x + dx * scale, projected.y, cast.origin.z + dz * scale);
        }
        BlockPos column = BlockPos.ofFloored(projected.x, cast.apex.y + 2, projected.z);
        if (!cast.world.isChunkLoaded(column)) return null;
        var ground = cast.world.raycast(new RaycastContext(new Vec3d(projected.x, cast.apex.y + 2, projected.z),
                new Vec3d(projected.x, Math.max(cast.world.getBottomY(), cast.origin.y - 24), projected.z),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, player));
        if (ground.getType() == HitResult.Type.MISS || ground.getSide() != net.minecraft.util.math.Direction.UP) return null;
        Vec3d target = ground.getPos().add(0, .01, 0);
        return safe(player, target) ? target : null;
    }

    static boolean safe(ServerPlayerEntity player, Vec3d position) {
        ServerWorld world = player.getServerWorld();
        if (position == null || position.y < world.getBottomY() || position.y + player.getHeight() >= world.getTopY()
                || !world.isChunkLoaded(BlockPos.ofFloored(position))
                || !world.getWorldBorder().contains(BlockPos.ofFloored(position))) return false;
        Box box = player.getBoundingBox().offset(position.subtract(player.getPos()));
        BlockPos support = BlockPos.ofFloored(position.add(0, -.05, 0));
        return world.isSpaceEmpty(player, box) && world.getFluidState(BlockPos.ofFloored(position)).isEmpty()
                && world.getFluidState(support).isEmpty()
                && !world.getBlockState(support).getCollisionShape(world, support).isEmpty();
    }

    private static boolean clearPath(ServerPlayerEntity player, Vec3d next) {
        Vec3d current = player.getPos();
        int samples = Math.max(1, (int) Math.ceil(current.distanceTo(next) / .25));
        for (int i = 1; i <= samples; i++) {
            Vec3d step = current.lerp(next, i / (double) samples);
            if (!player.getServerWorld().isChunkLoaded(BlockPos.ofFloored(step))
                    || !player.getServerWorld().isSpaceEmpty(player,
                    player.getBoundingBox().offset(step.subtract(current)))) return false;
        }
        return true;
    }

    private static void move(ServerPlayerEntity player, Vec3d position) {
        player.networkHandler.requestTeleport(position.x, position.y, position.z, player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
    }

    private static void velocity(LivingEntity entity, Vec3d velocity) {
        entity.setVelocity(velocity);
        entity.velocityModified = true;
        entity.velocityDirty = true;
        if (entity instanceof ServerPlayerEntity player && player.networkHandler != null)
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static void restore(Cast cast) {
        cast.player.setNoGravity(cast.previousNoGravity);
        cast.player.fallDistance = 0;
        velocity(cast.player, Vec3d.ZERO);
        LANDING_GRACE.put(cast.player, tick + 5);
    }

    private static void impact(Cast cast) {
        double radius = cast.settings.radius();
        PlayerMetadata attacker = new PlayerMetadata(MMOPlayerData.setup(cast.player).getStatMap(), EquipmentSlot.MAIN_HAND);
        Box area = new Box(cast.target, cast.target).expand(radius, 3, radius);
        for (LivingEntity target : cast.world.getEntitiesByClass(LivingEntity.class, area,
                entity -> entity != cast.player && entity.isAlive() && !entity.isSpectator())) {
            double dx = target.getX() - cast.target.x, dz = target.getZ() - cast.target.z;
            if (dx * dx + dz * dz > radius * radius || Math.abs(target.getY() - cast.target.y) > 3) continue;
            if(!vn.svframe.svframelib.entity.RpgEntityAdapters.allows(cast.player,target,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.DAMAGE))continue;
            if (target instanceof ServerPlayerEntity other && (!cast.world.getServer().isPvpEnabled()
                    || other.getAbilities().creativeMode || cast.player.isTeammate(other))) continue;
            boolean applied = cast.settings.damage() <= 0 || SVFrameLib.inst().getDamage().registerAttack(
                    new AttackMetadata(new DamageMetadata(cast.cursedExecution?ReferenceClassSkillRuntime.executionDamage(cast.player,target,cast.settings.damage()):cast.settings.damage(), java.util.List.of(DamageType.MAGIC, DamageType.SKILL)),
                            target, attacker), false);
            if (applied && target.isAlive()) velocity(target, new Vec3d(target.getVelocity().x,
                    Math.max(target.getVelocity().y, cast.settings.knockup()), target.getVelocity().z));
        }
        if(cast.deathSentence){DeathSentencePresentation.impacted(cast.player,cast.target);return;}
        ring(cast, cast.target, GOLD);
        cast.world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, cast.target.x, cast.target.y + .3,
                cast.target.z, 1, 0, 0, 0, 0);
        cast.world.playSound(null, BlockPos.ofFloored(cast.target), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4f, .65f);
    }

    private static void ring(Cast cast, Vec3d center, ParticleEffect particle) {
        int points=cast.deathSentence?80:64;
        for (int i = 0; i < points; i++) {
            double angle = i * Math.PI * 2 / points;
            particles(cast, particle, center.add(Math.cos(angle) * cast.settings.radius(), .12,
                    Math.sin(angle) * cast.settings.radius()));
        }
    }

    private static void particles(Cast cast, ParticleEffect particle, Vec3d position) {
        for (ServerPlayerEntity viewer : cast.world.getPlayers())
            if (viewer.squaredDistanceTo(position) <= 80 * 80)
                cast.world.spawnParticles(viewer, particle, true, position.x, position.y, position.z, 1, 0, 0, 0, 0);
    }

    private static final class Cast {
        final ServerPlayerEntity player;
        final ServerWorld world;
        Vec3d origin, apex;
        final boolean previousNoGravity;
        final HeroEntranceSettings settings;
        Phase phase = Phase.LAUNCH;
        Vec3d target, descentStart;
        int age, descentAge;
        boolean cursedExecution,deathSentence;
        Cast(ServerPlayerEntity player, HeroEntranceSettings settings, Vec3d apex) {
            this.player = player; this.world = player.getServerWorld(); this.origin = player.getPos();
            this.apex = apex; this.settings = settings; this.previousNoGravity = player.hasNoGravity();
        }
    }
}
