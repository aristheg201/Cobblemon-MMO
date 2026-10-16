package vn.svframe.fantasyhub.core;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.fantasyhub.protocol.Protocol;
import vn.svframe.svframelib.config.YamlLite;

/** Sends a bounded semantic event. All particle creation lives on the client. */
final class KabutoEffects {
  private static Map<String, Object> settings = Map.of();
  private static long budgetTick = Long.MIN_VALUE;
  private static int sent;

  static void reload() throws java.io.IOException {
    Path file = FabricLoader.getInstance().getConfigDir().resolve("FantasyHub/kabuto-vfx.yml");
    if (!Files.exists(file)) {
      Files.createDirectories(file.getParent());
      try (var in =
          KabutoEffects.class.getResourceAsStream("/default/kabuto/FantasyHub/kabuto-vfx.yml")) {
        if (in == null) throw new java.io.IOException("Missing packaged Kabuto VFX defaults");
        Files.copy(in, file);
      }
    }
    settings = validate(YamlLite.map(YamlLite.parse(file)));
  }

  static Map<String, Object> validate(Map<String, Object> next) {
    for (String key : List.of("enabled", "minecraft", "cobblemon"))
      if (!(next.get(key) instanceof Boolean))
        throw new IllegalArgumentException("Kabuto VFX " + key + " must be boolean");
    bounded(next, "view-distance", 8, 64);
    bounded(next, "density", 0, 1.5);
    bounded(next, "max-events-per-tick", 1, 256);
    if (((Number) next.get("max-events-per-tick")).doubleValue()
        != ((Number) next.get("max-events-per-tick")).intValue())
      throw new IllegalArgumentException("Kabuto VFX max-events-per-tick must be integer");
    return Map.copyOf(next);
  }

  private static void bounded(Map<String, Object> data, String key, double min, double max) {
    if (!(data.get(key) instanceof Number n)
        || !Double.isFinite(n.doubleValue())
        || n.doubleValue() < min
        || n.doubleValue() > max)
      throw new IllegalArgumentException(
          "Kabuto VFX " + key + " must be from " + min + " to " + max);
  }

  static void emit(
      ServerPlayerEntity actor,
      String phase,
      Vec3d start,
      Vec3d end,
      boolean hyper,
      int duration,
      double radius) {
    if (!Boolean.TRUE.equals(settings.get("enabled")) || !actor.isAlive() || actor.isDisconnected())
      return;
    long now = actor.getServer().getTicks();
    if (now != budgetTick) {
      budgetTick = now;
      sent = 0;
    }
    int flags =
        (Boolean.TRUE.equals(settings.get("minecraft")) ? 1 : 0)
            | (Boolean.TRUE.equals(settings.get("cobblemon")) ? 2 : 0);
    if (flags == 0) return;
    float density = ((Number) settings.get("density")).floatValue();
    if (density <= 0) return;
    var packet =
        new Protocol.KabutoFx(
            actor.getId(),
            phase,
            start.x,
            start.y,
            start.z,
            end.x,
            end.y,
            end.z,
            hyper,
            Math.clamp(duration, 1, 1200),
            (float) Math.clamp(radius, .1, 32),
            density,
            flags);
    Set<ServerPlayerEntity> viewers = new LinkedHashSet<>();
    viewers.add(actor);
    viewers.addAll(PlayerLookup.tracking(actor));
    double distance = ((Number) settings.get("view-distance")).doubleValue();
    int limit = ((Number) settings.get("max-events-per-tick")).intValue();
    for (var viewer : viewers) {
      if (sent >= limit) break;
      if (viewer.getWorld() != actor.getWorld()
          || Math.min(viewer.squaredDistanceTo(start), viewer.squaredDistanceTo(end))
              > distance * distance
          || !ServerPlayNetworking.canSend(viewer, Protocol.KabutoFx.ID)) continue;
      ServerPlayNetworking.send(viewer, packet);
      sent++;
    }
  }
}
