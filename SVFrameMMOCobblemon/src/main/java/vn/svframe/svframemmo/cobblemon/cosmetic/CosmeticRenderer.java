package vn.svframe.svframemmo.cobblemon.cosmetic;

import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded player-cosmetic renderer.
 *
 * AURA/HEAD/BACK/ORBIT are live player anchors. TRAIL and FOOTSTEP are movement emitters and are deliberately
 * the only slots that leave emissions behind the player. Particle ids are entirely definition-driven.
 *
 * The pseudo namespace svframe_dust renders vanilla DustParticleEffect directly and therefore never requires
 * a resource pack. Format: svframe_dust:RRGGBB/scale, for example svframe_dust:7a0019/0.9.
 */
final class CosmeticRenderer {
    private static final String DUST_NAMESPACE = "svframe_dust";
    private static final Map<String, DustParticleEffect> DUST_CACHE = new ConcurrentHashMap<>();

    private final Map<AmbientKey, Ambient> ambient = new ConcurrentHashMap<>();
    private long packetTick = Long.MIN_VALUE;
    private int packetsThisTick;

    void trigger(ServerPlayerEntity player, CosmeticDefinition definition, CosmeticDefinition.Trigger trigger) {
        if (player == null || definition == null) return;
        for (CosmeticDefinition.Phase phase : definition.phases(trigger)) {
            for (int i = 0; i < phase.repetitions(); i++) {
                final int emission = i;
                long at = SVFrameMMO.currentTick() + phase.delayTicks() + (long) i * phase.intervalTicks();
                SVFrameMMO.delayedActions().schedule(at,
                        () -> emitAtCurrentAnchor(player, definition, phase, SVFrameMMO.currentTick(), emission, 0d));
            }
        }
    }

    void equip(ServerPlayerEntity player, CosmeticDefinition definition) {
        trigger(player, definition, CosmeticDefinition.Trigger.EQUIP);
        trigger(player, definition, CosmeticDefinition.Trigger.EQUIP_BURST);
        int index = 0;
        for (CosmeticDefinition.Phase phase : definition.phases(CosmeticDefinition.Trigger.WHILE_EQUIPPED)) {
            AmbientKey key = new AmbientKey(player.getUuid(), definition.id(), index++);
            ambient.put(key, new Ambient(player.getUuid(), definition.id(), phase));
        }
    }

    void unequip(ServerPlayerEntity player, CosmeticDefinition definition) {
        if (player != null) trigger(player, definition, CosmeticDefinition.Trigger.UNEQUIP);
        UUID id = player == null ? null : player.getUuid();
        if (id != null)
            ambient.keySet().removeIf(key -> key.player().equals(id) && key.cosmetic().equals(definition.id()));
    }

    void clearPlayer(UUID player) {
        if (player != null) ambient.keySet().removeIf(key -> key.player().equals(player));
    }

    void clear() { ambient.clear(); }

    void tick(long tick, MinecraftServer server, CosmeticService service) {
        if (ambient.isEmpty()) return;
        for (Map.Entry<AmbientKey, Ambient> entry : ambient.entrySet()) {
            Ambient value = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(value.player);
            CosmeticDefinition definition = service.definition(value.cosmetic);
            if (player == null || player.isDisconnected() || definition == null
                    || !service.isEquipped(value.player, value.cosmetic)) {
                ambient.remove(entry.getKey(), value);
                continue;
            }

            int interval = Math.max(1, value.phase.intervalTicks());
            if (tick % interval != 0L) continue;

            CosmeticDefinition.Slot slot = definition.slot();
            if (slot == CosmeticDefinition.Slot.TRAIL || slot == CosmeticDefinition.Slot.FOOTSTEP) {
                tickMovementEmitter(tick, player, definition, value);
            } else {
                emitAtCurrentAnchor(player, definition, value.phase, tick, (int) (tick / interval), 0d);
            }
        }
    }

    private void tickMovementEmitter(long tick, ServerPlayerEntity player, CosmeticDefinition definition, Ambient ambient) {
        Vec3d current = player.getPos();
        if (ambient.lastPosition == null) {
            ambient.lastPosition = current;
            return;
        }

        double threshold = Math.max(0.05d, ambient.phase.movementThreshold());
        if (current.squaredDistanceTo(ambient.lastPosition) < threshold * threshold) return;
        if (definition.slot() == CosmeticDefinition.Slot.FOOTSTEP && !player.isOnGround()) {
            ambient.lastPosition = current;
            return;
        }

        Vec3d trailBase = ambient.lastPosition.add(0d, 0.08d, 0d);
        double side = 0d;
        if (definition.slot() == CosmeticDefinition.Slot.FOOTSTEP) {
            ambient.footstepRight = !ambient.footstepRight;
            side = ambient.footstepRight ? 0.18d : -0.18d;
        }
        Vec3d origin = localOffset(player, trailBase,
                ambient.phase.offsetX() + side, ambient.phase.offsetY(), ambient.phase.offsetZ());
        emitAt(player, definition, ambient.phase, origin);
        ambient.lastPosition = current;
    }

    private void emitAtCurrentAnchor(ServerPlayerEntity player, CosmeticDefinition definition,
                                     CosmeticDefinition.Phase phase, long tick, int emissionIndex, double extraLocalX) {
        if (player.isDisconnected() || !player.isAlive()) return;
        Vec3d origin = resolveAnchor(player, definition.slot(), phase, tick, emissionIndex, extraLocalX);
        emitAt(player, definition, phase, origin);
    }

    private Vec3d resolveAnchor(ServerPlayerEntity player, CosmeticDefinition.Slot slot,
                                CosmeticDefinition.Phase phase, long tick, int emissionIndex, double extraLocalX) {
        CosmeticDefinition.Anchor anchor = phase.anchor();
        Vec3d base = switch (anchor) {
            case FEET -> player.getPos().add(0d, 0.08d, 0d);
            case HEAD -> player.getEyePos().add(0d, 0.10d, 0d);
            case BACK -> player.getPos().add(0d, player.getHeight() * 0.62d, 0d);
            case ORBIT -> player.getPos().add(0d, player.getHeight() * 0.50d, 0d);
            case BODY -> player.getPos().add(0d, player.getHeight() * 0.50d, 0d);
        };

        if (anchor == CosmeticDefinition.Anchor.ORBIT || slot == CosmeticDefinition.Slot.ORBIT) {
            int period = Math.max(1, phase.orbitPeriodTicks());
            double angle = Math.PI * 2d * ((tick + emissionIndex) % period) / period;
            base = base.add(Math.cos(angle) * phase.orbitRadius(), 0d, Math.sin(angle) * phase.orbitRadius());
        }

        return localOffset(player, base, phase.offsetX() + extraLocalX, phase.offsetY(), phase.offsetZ());
    }

    private static Vec3d localOffset(ServerPlayerEntity player, Vec3d base, double localX, double localY, double localZ) {
        Vec3d look = player.getRotationVec(1f);
        Vec3d forward = new Vec3d(look.x, 0d, look.z);
        if (forward.lengthSquared() < 1.0e-8d) forward = new Vec3d(0d, 0d, 1d);
        else forward = forward.normalize();
        Vec3d right = new Vec3d(-forward.z, 0d, forward.x);
        return base.add(right.multiply(localX)).add(0d, localY, 0d).add(forward.multiply(localZ));
    }

    private void emitAt(ServerPlayerEntity caster, CosmeticDefinition cosmetic,
                        CosmeticDefinition.Phase phase, Vec3d origin) {
        if (caster.isDisconnected() || !caster.isAlive()) return;
        Identifier particle = Identifier.tryParse(cosmetic.particleId());
        if (particle == null) return;

        DustParticleEffect dust = DUST_NAMESPACE.equals(particle.getNamespace())
                ? dustEffect(particle.getPath()) : null;
        if (DUST_NAMESPACE.equals(particle.getNamespace()) && dust == null) return;

        int viewerLimit = phase.maxViewers();
        int emitted = 0;
        for (ServerPlayerEntity viewer : PlayerLookup.around(caster.getServerWorld(), origin, phase.broadcastRadius())) {
            if (viewer.isDisconnected()) continue;
            if (emitted++ >= viewerLimit) break;

            if (dust != null) {
                if (claimPacket()) sendDust(viewer, dust, origin);
                continue;
            }

            boolean nativeSnowstorm = "cobblemon".equals(particle.getNamespace())
                    || "mega_showdown".equals(particle.getNamespace());
            boolean snowstormReady = nativeSnowstorm
                    || (SnowstormPackService.ready() && PolymerResourcePackUtils.hasMainPack(viewer));
            if (snowstormReady && claimPacket())
                new SpawnSnowstormParticlePacket(particle, origin).sendToPlayer(viewer);
            else if (!cosmetic.hideWithoutResourcePack())
                sendFallback(viewer, cosmetic.fallback(), origin);
        }
    }

    private static DustParticleEffect dustEffect(String path) {
        return DUST_CACHE.computeIfAbsent(path, CosmeticRenderer::parseDustEffect);
    }

    private static DustParticleEffect parseDustEffect(String path) {
        try {
            String[] parts = path.split("/", 2);
            if (!parts[0].matches("[0-9a-fA-F]{6}")) return null;
            int rgb = Integer.parseInt(parts[0], 16);
            float red = ((rgb >> 16) & 0xFF) / 255f;
            float green = ((rgb >> 8) & 0xFF) / 255f;
            float blue = (rgb & 0xFF) / 255f;
            float scale = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            if (!Float.isFinite(scale)) return null;
            scale = Math.max(0.01f, Math.min(4.0f, scale));
            return new DustParticleEffect(new Vector3f(red, green, blue), scale);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void sendDust(ServerPlayerEntity viewer, DustParticleEffect effect, Vec3d origin) {
        viewer.networkHandler.sendPacket(new ParticleS2CPacket(effect, false,
                origin.x, origin.y, origin.z,
                0f, 0f, 0f, 0f, 1));
    }

    private void sendFallback(ServerPlayerEntity viewer, CosmeticDefinition.Fallback fallback, Vec3d origin) {
        if (!fallback.enabled()) return;
        Identifier id = Identifier.tryParse(fallback.particleId());
        if (id == null) return;
        var type = Registries.PARTICLE_TYPE.get(id);
        if (!(type instanceof SimpleParticleType effect)) return;
        int count = Math.min(fallback.count(),
                Math.max(1, SVFrameMMOCobblemon.config().vfx.maxFallbackParticlesPerEmission));
        viewer.networkHandler.sendPacket(new ParticleS2CPacket(effect, false,
                origin.x, origin.y, origin.z,
                (float) fallback.spread(), (float) fallback.spread(), (float) fallback.spread(),
                (float) fallback.speed(), count));
    }

    private synchronized boolean claimPacket() {
        long tick = SVFrameMMO.currentTick();
        if (tick != packetTick) {
            packetTick = tick;
            packetsThisTick = 0;
        }
        int max = Math.max(1, SVFrameMMOCobblemon.config().vfx.maxSnowstormPacketsPerTick);
        if (packetsThisTick >= max) return false;
        packetsThisTick++;
        return true;
    }

    private record AmbientKey(UUID player, String cosmetic, int phase) { }

    private static final class Ambient {
        private final UUID player;
        private final String cosmetic;
        private final CosmeticDefinition.Phase phase;
        private Vec3d lastPosition;
        private boolean footstepRight;

        private Ambient(UUID player, String cosmetic, CosmeticDefinition.Phase phase) {
            this.player = player;
            this.cosmetic = cosmetic;
            this.phase = phase;
        }
    }
}
