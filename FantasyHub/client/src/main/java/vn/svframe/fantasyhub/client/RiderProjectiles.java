package vn.svframe.fantasyhub.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.*;
import org.joml.Vector3f;
import vn.svframe.fantasyhub.protocol.Protocol;

/**
 * Finite client visuals interpolate compact spawn events. They never choose hits or apply damage.
 */
final class RiderProjectiles {
  private record Visual(Protocol.RiderProjectile packet, long born) {}

  private static final Map<Long, Visual> ACTIVE = new HashMap<>();
  private static long received;

  static void accept(Protocol.RiderProjectile p) {
    long key = ((long) p.entityId() << 32) | (p.token() & 0xffffffffL);
    if (!p.active()) {
      ACTIVE.remove(key);
      return;
    }
    var client = MinecraftClient.getInstance();
    if (client.world == null
        || p.lifetime() < 1
        || p.lifetime() > 64
        || p.token() < 1
        || !Double.isFinite(p.x())
        || !Double.isFinite(p.y())
        || !Double.isFinite(p.z())
        || !Float.isFinite(p.vx())
        || !Float.isFinite(p.vy())
        || !Float.isFinite(p.vz())
        || new Vec3d(p.vx(), p.vy(), p.vz()).lengthSquared() > 4
        || client
                .gameRenderer
                .getCamera()
                .getPos()
                .squaredDistanceTo(new Vec3d(p.x(), p.y(), p.z()))
            > 6400
        || ACTIVE.size() >= 128) return;
    ACTIVE.put(key, new Visual(p, client.world.getTime()));
    received++;
  }

  static void clear() {
    ACTIVE.clear();
  }

  static com.google.gson.JsonObject observation() {
    var result = new com.google.gson.JsonObject();
    result.addProperty("received", received);
    result.addProperty("active", ACTIVE.size());
    return result;
  }

  static void tick() {
    var client = MinecraftClient.getInstance();
    if (client.world == null) {
      clear();
      return;
    }
    ACTIVE
        .values()
        .removeIf(
            v ->
                client.world.getTime() - v.born >= v.packet.lifetime()
                    || RiderVisuals.state(v.packet.entityId()) == null);
    for (var v : ACTIVE.values()) {
      Vec3d at = position(v, client.world.getTime() - v.born);
      if (client.gameRenderer.getCamera().getPos().squaredDistanceTo(at) > 4096) continue;
      var dust =
          new net.minecraft.particle.DustParticleEffect(
              new Vector3f(
                  v.packet.fire() ? 1 : .9f,
                  v.packet.fire() ? .25f : .12f,
                  v.packet.fire() ? .05f : .7f),
              .6f);
      RiderEffects.point(dust, at);
      RiderEffects.point(v.packet.fire() ? ParticleTypes.FLAME : ParticleTypes.ELECTRIC_SPARK, at);
    }
  }

  static void render(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext context) {
    var client = MinecraftClient.getInstance();
    if (client.world == null || context.matrixStack() == null) return;
    for (var v : ACTIVE.values()) {
      if (v.packet.fire()) continue;
      double age = client.world.getTime() - v.born + context.tickCounter().getTickDelta(false);
      Vec3d at = position(v, age), camera = context.camera().getPos();
      var matrices = context.matrixStack();
      matrices.push();
      matrices.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
      matrices.multiply(
          RotationAxis.POSITIVE_Y.rotation((float) -Math.atan2(v.packet.vx(), v.packet.vz())));
      matrices.multiply(RotationAxis.POSITIVE_Z.rotation((float) age * .45f));
      matrices.scale(.2f, .2f, .2f);
      RiderModels.object(
          "rider_card",
          matrices,
          client.getBufferBuilders().getEntityVertexConsumers(),
          0xf000f0,
          0xcfffe1f5);
      matrices.pop();
    }
  }

  private static Vec3d position(Visual v, double age) {
    var p = v.packet;
    return new Vec3d(p.x() + p.vx() * age, p.y() + p.vy() * age, p.z() + p.vz() * age);
  }
}
