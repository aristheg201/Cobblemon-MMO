package vn.svframe.svframemmo.cobblemon.mixin;

import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormParticlePacket;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticDefinition;
import vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticEmitterMetadata;
import vn.svframe.svframemmo.cobblemon.cosmetic.SnowstormPackService;

/** Dispatches configured cosmetic phases to native Minecraft or Cobblemon Snowstorm particle backends. */
@Mixin(targets = "vn.svframe.svframemmo.cobblemon.cosmetic.CosmeticRenderer", remap = false)
public abstract class CosmeticRendererBackendMixin {
    @Shadow(remap = false)
    private boolean claimPacket() { throw new AssertionError(); }

    @Shadow(remap = false)
    private void sendFallback(ServerPlayerEntity viewer, CosmeticDefinition.Fallback fallback, Vec3d origin) {
        throw new AssertionError();
    }

    @Inject(method = "emitAt", at = @At("HEAD"), cancellable = true, remap = false)
    private void svframe$dispatchParticleBackend(ServerPlayerEntity caster,
                                                  CosmeticDefinition cosmetic,
                                                  CosmeticDefinition.Phase phase,
                                                  Vec3d origin,
                                                  CallbackInfo ci) {
        CosmeticEmitterMetadata.Emitter emitter = CosmeticEmitterMetadata.emitter(cosmetic, phase);
        if (emitter == null) return;
        ci.cancel();
        if (caster.isDisconnected() || !caster.isAlive()) return;

        Identifier particle = Identifier.tryParse(emitter.particleId());
        if (particle == null) return;
        CosmeticEmitterMetadata.Backend backend = emitter.backend() == CosmeticEmitterMetadata.Backend.AUTO
                ? inferBackend(particle)
                : emitter.backend();

        int emitted = 0;
        for (ServerPlayerEntity viewer : PlayerLookup.around(caster.getServerWorld(), origin, phase.broadcastRadius())) {
            if (viewer.isDisconnected()) continue;
            if (emitted++ >= phase.maxViewers()) break;

            boolean available = backend == CosmeticEmitterMetadata.Backend.MINECRAFT
                    ? minecraftAvailable(particle)
                    : snowstormAvailable(viewer, particle);
            if (!available) {
                if (!cosmetic.hideWithoutResourcePack()) sendFallback(viewer, cosmetic.fallback(), origin);
                continue;
            }
            if (!claimPacket()) break;

            if (backend == CosmeticEmitterMetadata.Backend.MINECRAFT)
                sendMinecraft(viewer, particle, emitter, origin);
            else
                new SpawnSnowstormParticlePacket(particle, origin).sendToPlayer(viewer);
        }
    }

    private static CosmeticEmitterMetadata.Backend inferBackend(Identifier particle) {
        return "minecraft".equals(particle.getNamespace())
                ? CosmeticEmitterMetadata.Backend.MINECRAFT
                : CosmeticEmitterMetadata.Backend.COBBLEMON;
    }

    private static boolean minecraftAvailable(Identifier particle) {
        if ("minecraft".equals(particle.getNamespace()) && "dust".equals(particle.getPath())) return true;
        return Registries.PARTICLE_TYPE.get(particle) instanceof SimpleParticleType;
    }

    private static boolean snowstormAvailable(ServerPlayerEntity viewer, Identifier particle) {
        boolean nativeSnowstorm = "cobblemon".equals(particle.getNamespace())
                || "mega_showdown".equals(particle.getNamespace());
        return nativeSnowstorm || (SnowstormPackService.ready() && PolymerResourcePackUtils.hasMainPack(viewer));
    }

    private static void sendMinecraft(ServerPlayerEntity viewer, Identifier particle,
                                      CosmeticEmitterMetadata.Emitter emitter, Vec3d origin) {
        ParticleEffect effect;
        if ("minecraft".equals(particle.getNamespace()) && "dust".equals(particle.getPath())) {
            int rgb = emitter.colorRgb();
            effect = new DustParticleEffect(new Vector3f(
                    ((rgb >> 16) & 0xFF) / 255f,
                    ((rgb >> 8) & 0xFF) / 255f,
                    (rgb & 0xFF) / 255f), emitter.scale());
        } else {
            var type = Registries.PARTICLE_TYPE.get(particle);
            if (!(type instanceof SimpleParticleType simple)) return;
            effect = simple;
        }

        viewer.networkHandler.sendPacket(new ParticleS2CPacket(effect, false,
                origin.x, origin.y, origin.z,
                (float) emitter.spread(), (float) emitter.spread(), (float) emitter.spread(),
                (float) emitter.speed(), emitter.count()));
    }
}
