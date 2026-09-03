package vn.svframe.svframemmo.cobblemon.cosmetic;

import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.cobblemon.SVFrameMMOCobblemon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded Snowstorm + vanilla fallback renderer. Only equipped ambient cosmetics are ticked. */
final class CosmeticRenderer {
    private final Map<AmbientKey, Ambient> ambient = new ConcurrentHashMap<>();
    private long packetTick = Long.MIN_VALUE;
    private int packetsThisTick;

    void trigger(ServerPlayerEntity player, CosmeticDefinition definition, CosmeticDefinition.Trigger trigger, Vec3d target) {
        if (player == null || definition == null) return;
        for (CosmeticDefinition.Phase phase : definition.phases(trigger)) {
            for (int i = 0; i < phase.repetitions(); i++) {
                final int emission = i;
                long at = SVFrameMMO.currentTick() + phase.delayTicks() + (long) i * phase.intervalTicks();
                SVFrameMMO.delayedActions().schedule(at, () -> emit(player, definition, phase, target, emission));
            }
        }
    }

    void equip(ServerPlayerEntity player, CosmeticDefinition definition) {
        trigger(player, definition, CosmeticDefinition.Trigger.EQUIP, null);
        trigger(player, definition, CosmeticDefinition.Trigger.EQUIP_BURST, null);
        for (CosmeticDefinition.Phase phase : definition.phases(CosmeticDefinition.Trigger.WHILE_EQUIPPED))
            ambient.put(new AmbientKey(player.getUuid(), definition.id()), new Ambient(player.getUuid(), definition.id(), phase));
    }

    void unequip(ServerPlayerEntity player, CosmeticDefinition definition) {
        if (player != null) trigger(player, definition, CosmeticDefinition.Trigger.UNEQUIP, null);
        UUID id = player == null ? null : player.getUuid();
        ambient.keySet().removeIf(key -> key.player().equals(id) && key.cosmetic().equals(definition.id()));
    }

    void clearPlayer(UUID player) { if (player != null) ambient.keySet().removeIf(key -> key.player().equals(player)); }
    void clear() { ambient.clear(); }

    void tick(long tick, MinecraftServer server, CosmeticService service) {
        if (ambient.isEmpty()) return;
        // ConcurrentHashMap iterators are weakly consistent, so there is no need to allocate Map.copyOf(ambient)
        // every server tick. Invalid entries are removed conditionally as they are observed.
        for (Map.Entry<AmbientKey, Ambient> entry : ambient.entrySet()) {
            Ambient value = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(value.player());
            CosmeticDefinition definition = service.definition(value.cosmetic());
            if (player == null || player.isDisconnected() || definition == null || !service.isEquipped(value.player(), value.cosmetic())) {
                ambient.remove(entry.getKey(), value);
                continue;
            }
            int interval = Math.max(1, value.phase().intervalTicks());
            if (tick % interval == 0) emit(player, definition, value.phase(), null, (int) (tick / interval));
        }
    }

    private void emit(ServerPlayerEntity caster, CosmeticDefinition cosmetic, CosmeticDefinition.Phase phase, Vec3d target, int emissionIndex) {
        if (caster.isDisconnected() || !caster.isAlive()) return;
        Vec3d anchor = switch (phase.anchor()) {
            case TARGET -> target == null ? caster.getPos() : target;
            case CAST_POSITION, CASTER -> caster.getPos();
        };
        final Vec3d origin = anchor.add(phase.offsetX(), phase.offsetY(), phase.offsetZ());
        Identifier particle = Identifier.tryParse(cosmetic.particleId());
        if (particle == null) return;

        int viewerLimit = phase.maxViewers();
        int emitted = 0;
        for (ServerPlayerEntity viewer : PlayerLookup.around(caster.getServerWorld(), origin, phase.broadcastRadius())) {
            if (viewer.isDisconnected()) continue;
            if (emitted++ >= viewerLimit) break;

            // Native Cobblemon/Mega Showdown Snowstorm definitions are already present in those client mods and do
            // not depend on the generated Polymer pack. Only integration-owned custom definitions require that pack.
            boolean nativeSnowstorm = "cobblemon".equals(particle.getNamespace()) || "mega_showdown".equals(particle.getNamespace());
            boolean snowstormReady = nativeSnowstorm || (SnowstormPackService.ready() && PolymerResourcePackUtils.hasMainPack(viewer));
            if (snowstormReady && claimPacket()) new SpawnSnowstormParticlePacket(particle, origin).sendToPlayer(viewer);
            else if (!cosmetic.hideWithoutResourcePack()) sendFallback(viewer, cosmetic.fallback(), origin);
        }
    }

    private void sendFallback(ServerPlayerEntity viewer, CosmeticDefinition.Fallback fallback, Vec3d origin) {
        if (!fallback.enabled()) return;
        Identifier id = Identifier.tryParse(fallback.particleId());
        if (id == null) return;
        var type = Registries.PARTICLE_TYPE.get(id);
        if (!(type instanceof SimpleParticleType effect)) return;
        int count = Math.min(fallback.count(), Math.max(1, SVFrameMMOCobblemon.config().vfx.maxFallbackParticlesPerEmission));
        viewer.networkHandler.sendPacket(new ParticleS2CPacket(effect, false, origin.x, origin.y, origin.z,
                (float) fallback.spread(), (float) fallback.spread(), (float) fallback.spread(),
                (float) fallback.speed(), count));
    }

    private synchronized boolean claimPacket() {
        long tick = SVFrameMMO.currentTick();
        if (tick != packetTick) { packetTick = tick; packetsThisTick = 0; }
        int max = Math.max(1, SVFrameMMOCobblemon.config().vfx.maxSnowstormPacketsPerTick);
        if (packetsThisTick >= max) return false;
        packetsThisTick++;
        return true;
    }

    private record AmbientKey(UUID player, String cosmetic) { }
    private record Ambient(UUID player, String cosmetic, CosmeticDefinition.Phase phase) { }
}
