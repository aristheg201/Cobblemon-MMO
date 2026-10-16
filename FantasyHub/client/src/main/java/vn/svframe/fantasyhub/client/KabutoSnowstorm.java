package vn.svframe.fantasyhub.client;

import com.google.gson.JsonParser;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Optional, cached Cobblemon 1.8 Snowstorm API. No copied particle implementation. */
final class KabutoSnowstorm {
  private static boolean initialized, ready;
  private static Object repository;
  private static Constructor<?> matrixConstructor, stormConstructor;
  private static Method getEffect, updateMatrix, updatePosition, spawn, particles;
  private static final List<WeakReference<Object>> active = new ArrayList<>();
  private static final Map<String, Long> emitted = new LinkedHashMap<>();

  private record FixedEffect(Object original, Object corrected) {}

  private static final Map<String, FixedEffect> fixedEffects = new HashMap<>();
  private static final Map<String, String> textureRepairs = new LinkedHashMap<>();
  private static int peakParticles;
  private static String diagnostic = "not initialized";

  private static void initialize() {
    if (initialized) return;
    initialized = true;
    if (!FabricLoader.getInstance().isModLoaded("cobblemon")) {
      diagnostic = "Cobblemon not installed";
      return;
    }
    try {
      var repo =
          Class.forName(
              "com.cobblemon.mod.common.client.particle.BedrockParticleOptionsRepository");
      repository = repo.getField("INSTANCE").get(null);
      getEffect = repo.getMethod("getEffect", Identifier.class);
      var matrix = Class.forName("com.cobblemon.mod.common.client.render.MatrixWrapper");
      matrixConstructor = matrix.getConstructor();
      updateMatrix = matrix.getMethod("updateMatrix", Matrix4f.class);
      updatePosition = matrix.getMethod("updatePosition", Vec3d.class);
      var storm = Class.forName("com.cobblemon.mod.common.client.particle.ParticleStorm");
      // Same Kotlin default constructor used by Cobblemon's own packet handler.
      stormConstructor =
          Arrays.stream(storm.getConstructors())
              .filter(
                  c ->
                      c.getParameterCount() == 14
                          && c.getParameterTypes()[12] == int.class
                          && c.getParameterTypes()[1] == matrix)
              .findFirst()
              .orElseThrow();
      spawn = storm.getMethod("spawn");
      particles = storm.getMethod("getParticles");
      ready = true;
      diagnostic = "Cobblemon 1.8 ParticleStorm";
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      fail(failure);
    }
  }

  private static void fail(Throwable failure) {
    if (failure instanceof InvocationTargetException invocation && invocation.getCause() != null)
      failure = invocation.getCause();
    ready = false;
    diagnostic = failure.getClass().getSimpleName() + ": " + failure.getMessage();
    org.slf4j.LoggerFactory.getLogger("FantasyHub/Kabuto-VFX")
        .warn("Native Cobblemon VFX unavailable: {}", diagnostic, failure);
  }

  static boolean emit(String name, Vec3d origin, Vec3d direction) {
    initialize();
    var client = MinecraftClient.getInstance();
    if (!ready || client.world == null) return false;
    try {
      var id = Identifier.of("cobblemon", name);
      Object effect = getEffect.invoke(repository, id);
      if (effect == null) {
        diagnostic = "Missing native effect " + id;
        return false;
      }
      effect = validateTexture(client, name, effect);
      if (effect == null) return false;
      Object frame = matrixConstructor.newInstance();
      var rotation =
          new Matrix4f()
              .rotationY((float) Math.atan2(direction.x, direction.z))
              .rotateX(
                  (float) -Math.atan2(direction.y, Math.max(.001, direction.horizontalLength())));
      updateMatrix.invoke(frame, rotation);
      updatePosition.invoke(frame, origin);
      Object[] args = new Object[14];
      args[0] = effect;
      args[1] = frame;
      args[2] = frame;
      args[3] = client.world;
      args[12] = 4080;
      Object storm = stormConstructor.newInstance(args);
      spawn.invoke(storm);
      active.add(new WeakReference<>(storm));
      if (active.size() > 64) active.remove(0);
      emitted.merge(name, 1L, Long::sum);
      return true;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      fail(failure);
      return false;
    }
  }

  /** Repair two broken upstream descriptors privately, never altering Cobblemon's registry. */
  private static Object validateTexture(MinecraftClient client, String name, Object original)
      throws ReflectiveOperationException {
    Object effect = original;
    String descriptor =
        switch (name) {
          case "explosion_burst_flash" -> "moves/explosion/explosion_burst_flash";
          case "metal_sparks" -> "metal_sparks";
          default -> null;
        };
    if (descriptor != null) {
      var cached = fixedEffects.get(name);
      if (cached == null || cached.original() != original) {
        String sprite = name.equals("metal_sparks") ? "generic/orb/scaling" : "vanilla/flash";
        var resource =
            client
                .getResourceManager()
                .getResource(
                    Identifier.of(
                        "cobblemon", "bedrock/particles/" + descriptor + ".particle.json"));
        if (resource.isEmpty()) return null;
        try (var reader = resource.get().getReader()) {
          var json = JsonParser.parseReader(reader).getAsJsonObject();
          json.getAsJsonObject("particle_effect")
              .getAsJsonObject("description")
              .getAsJsonObject("basic_render_parameters")
              .addProperty("texture", "textures/particles/" + sprite);
          var parser = Class.forName("com.cobblemon.mod.common.particle.SnowstormParticleReader");
          effect =
              parser
                  .getMethod("loadEffect", com.google.gson.JsonObject.class)
                  .invoke(parser.getField("INSTANCE").get(null), json);
          fixedEffects.put(name, new FixedEffect(original, effect));
          textureRepairs.put(name, "cobblemon:" + sprite);
        } catch (java.io.IOException failure) {
          diagnostic = "Unreadable native descriptor " + descriptor;
          return null;
        }
      } else effect = cached.corrected();
    }
    Object particle = effect.getClass().getMethod("getParticle").invoke(effect);
    var texture = (Identifier) particle.getClass().getMethod("getTexture").invoke(particle);
    var png =
        Identifier.of(texture.getNamespace(), "textures/particle/" + texture.getPath() + ".png");
    if (client.getResourceManager().getResource(png).isEmpty()) {
      diagnostic = "Native particle texture unavailable: " + texture;
      return null;
    }
    return effect;
  }

  static void tick() {
    if (!ready) return;
    int count = 0;
    try {
      for (var it = active.iterator(); it.hasNext(); ) {
        Object storm = it.next().get();
        if (storm == null) {
          it.remove();
          continue;
        }
        count += ((List<?>) particles.invoke(storm)).size();
      }
      peakParticles = Math.max(peakParticles, count);
    } catch (ReflectiveOperationException | RuntimeException failure) {
      fail(failure);
    }
  }

  static Map<String, Object> observation() {
    return Map.of(
        "available",
        ready,
        "backend",
        diagnostic,
        "emitters",
        new LinkedHashMap<>(emitted),
        "textureRepairs",
        new LinkedHashMap<>(textureRepairs),
        "nativeParticlesPeak",
        peakParticles);
  }

  static void clear() {
    active.clear();
    emitted.clear();
    peakParticles = 0;
  }
}
