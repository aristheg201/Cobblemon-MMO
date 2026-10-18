package vn.svframe.fantasyhub.client;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.*;
import net.minecraft.util.math.*;
import org.joml.Vector3f;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Bounded, presentation-only card matrices, moving crests and translucent Rider phantoms. */
final class RiderEffects {
  private static final Set<String> PHASES =
      Set.of(
          "HENSHIN",
          "VENT",
          "AURA",
          "CARDS",
          "SURVIVE",
          "COMPLETE",
          "RESONANCE",
          "SCALES",
          "FIRE_RAIN",
          "FIRE_SLASH",
          "PURPLE_SLASH",
          "DRAGON_CREST",
          "KICK_IMPACT",
          "DRAGON_CHARGE",
          "DARK_SLASH",
          "SONIC",
          "CLOAK",
          "TORNADO",
          "DARK_RUSH",
          "MATRIX_SLASH",
          "CARD_HIT",
          "FIRE_BOLT",
          "MATRIX_BOLT",
          "CLOCK",
          "SPIRIT_1",
          "SPIRIT_2",
          "SPIRIT_3",
          "KNIGHT_PHANTOM",
          "DECADE_PHANTOM",
          "ULTIMATE_PHANTOM",
          "DIMENSION",
          "BOOKER_GUN");
  private static final List<Job> JOBS = new ArrayList<>();
  private static final Map<String, Long> OBSERVED = new TreeMap<>();
  private static ClientWorld world;
  private static long requests, nativeRequests;
  private static int budget;

  private static final class Job {
    final Protocol.RiderFx packet;
    final Vec3d start, end;
    int age;

    Job(Protocol.RiderFx p) {
      packet = p;
      start = new Vec3d(p.x(), p.y(), p.z());
      end = new Vec3d(p.tx(), p.ty(), p.tz());
    }
  }

  static void install() {
    net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_ENTITIES.register(
        context -> {
          var client = MinecraftClient.getInstance();
          if (client.world == null || context.matrixStack() == null) return;
          for (var job : JOBS) {
            String form =
                switch (job.packet.phase()) {
                  case "KNIGHT_PHANTOM" -> "knight";
                  case "DECADE_PHANTOM" -> "decade";
                  case "ULTIMATE_PHANTOM" -> "ryuki";
                  default -> "";
                };
            if (form.isEmpty()) continue;
            var matrices = context.matrixStack();
            var camera = context.camera().getPos();
            matrices.push();
            matrices.translate(
                job.start.x - camera.x, job.start.y - camera.y, job.start.z - camera.z);
            var direction = job.end.subtract(job.start);
            matrices.multiply(
                RotationAxis.POSITIVE_Y.rotation((float) -Math.atan2(direction.x, direction.z)));
            int alpha = (int) (145 * Math.min(1, (job.packet.duration() - job.age) / 6d));
            RiderModels.displayTint(
                form,
                matrices,
                client.getBufferBuilders().getEntityVertexConsumers(),
                0xf000f0,
                false,
                (alpha << 24) | 0x59e7ff);
            matrices.pop();
          }
        });
  }

  static void accept(Protocol.RiderFx p) {
    var client = MinecraftClient.getInstance();
    if (client.world == null
        || !PHASES.contains(p.phase())
        || !Set.of("RYUKI", "KNIGHT", "DECADE", "AGITO", "ZOLDA").contains(p.rider())
        || p.duration() < 1
        || p.duration() > 1200) return;
    if (!Double.isFinite(p.x())
        || !Double.isFinite(p.y())
        || !Double.isFinite(p.z())
        || !Double.isFinite(p.tx())
        || !Double.isFinite(p.ty())
        || !Double.isFinite(p.tz())) return;
    if (world != client.world) {
      clear();
      world = client.world;
    }
    if (client.gameRenderer.getCamera().getPos().squaredDistanceTo(new Vec3d(p.x(), p.y(), p.z()))
        > 4096) return;
    if (JOBS.size() >= 64) return;
    JOBS.add(new Job(p));
    OBSERVED.merge(p.rider() + ":" + p.phase(), 1L, Long::sum);
  }

  static boolean bookerGun(int entity) {
    return JOBS.stream()
        .anyMatch(j -> j.packet.entityId() == entity && j.packet.phase().equals("BOOKER_GUN"));
  }

  static void clear() {
    JOBS.clear();
    world = null;
  }

  static void tick() {
    var client = MinecraftClient.getInstance();
    if (world != client.world) {
      clear();
      world = client.world;
    }
    if (world == null) return;
    float density = FantasyHubClient.options == null ? 1 : FantasyHubClient.options.effectsDensity;
    budget = (int) (384 * density);
    if (client.options.getParticles().getValue().name().equals("MINIMAL"))
      budget = Math.min(budget, 80);
    else if (client.options.getParticles().getValue().name().equals("DECREASED"))
      budget = Math.min(budget, 160);
    int nativeBudget = 2;
    JOBS.sort(Comparator.comparingInt(j -> j.packet.phase().equals("DIMENSION") ? 0 : 1));
    for (var iterator = JOBS.iterator(); iterator.hasNext(); ) {
      var j = iterator.next();
      var p = j.packet;
      if (j.age >= p.duration()) {
        iterator.remove();
        continue;
      }
      var actor = world.getEntityById(p.entityId());
      if (actor == null || !actor.isAlive()) {
        iterator.remove();
        continue;
      }
      if (j.age == 0
          && density > 0
          && nativeBudget > 0
          && (FantasyHubClient.options == null || FantasyHubClient.options.cobblemonEffects)) {
        String effect =
            switch (p.phase()) {
              case "KICK_IMPACT", "RESONANCE" -> "explosion_burst_flash";
              case "FIRE_SLASH", "PURPLE_SLASH", "DARK_SLASH", "MATRIX_SLASH" ->
                  "scratch_targetsparks";
              case "DRAGON_CHARGE", "DARK_RUSH", "CLOCK" -> "quickattack_dashlines";
              case "HENSHIN", "SURVIVE", "COMPLETE", "SPIRIT_1", "SPIRIT_2", "SPIRIT_3" ->
                  "swordsdance_sparkle";
              default -> null;
            };
        if (effect != null) {
          if (KabutoSnowstorm.emit(effect, j.start, j.end.subtract(j.start).normalize()))
            nativeRequests++;
          nativeBudget--;
        }
      }
      Vec3d pos = actor.getPos();
      double t = j.age / (double) p.duration();
      ParticleEffect color =
          dust(
              p.rider().equals("RYUKI")
                  ? 0xff4b19
                  : p.rider().equals("KNIGHT") ? 0x609aff : 0xf137ae,
              .65f);
      switch (p.phase()) {
        case "DIMENSION" -> {
          Vec3d dir = j.end.subtract(j.start).normalize(), side = new Vec3d(dir.z, 0, -dir.x);
          for (int i = 1; i <= 7; i++)
            card(j.start.lerp(j.end, i / 8d).add(0, 1.1, 0), side, color, 1);
        }
        case "CARDS", "COMPLETE" -> {
          for (int i = 0; i < 6; i++) {
            double a = j.age * .04 + i * Math.PI / 3;
            card(
                pos.add(Math.cos(a) * .85, 1, Math.sin(a) * .85),
                new Vec3d(-Math.sin(a), 0, Math.cos(a)),
                color,
                .23);
          }
        }
        case "SCALES" -> {
          for (int i = 0; i < 2; i++) {
            double a = j.age * .1 + i * Math.PI;
            card(
                pos.add(Math.cos(a) * .8, 1, Math.sin(a) * .8),
                new Vec3d(-Math.sin(a), 0, Math.cos(a)),
                dust(0xdacaba, .8f),
                .45);
          }
        }
        case "CLOAK" -> {
          for (int i = 0; i < 38; i++) {
            double a = i * Math.PI / 19;
            point(color, pos.add(Math.cos(a) * .65, .2 + (i % 6) * .22, Math.sin(a) * .65));
          }
        }
        case "SONIC" -> {
          Vec3d dir = j.end.subtract(j.start).normalize(), side = new Vec3d(dir.z, 0, -dir.x);
          double range = .3 + t * 7;
          for (int i = 0; i < 24; i++) {
            double a = (i / 23d - .5) * 2;
            point(
                color,
                j.start
                    .add(dir.multiply(Math.cos(a) * range))
                    .add(side.multiply(Math.sin(a) * range)));
          }
        }
        case "HENSHIN", "TORNADO", "AURA", "SURVIVE", "RESONANCE" -> {
          for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16 + j.age * .2;
            point(
                color,
                pos.add(
                    Math.cos(a) * (.65 + i % 3 * .22),
                    .15 + i % 8 * .2,
                    Math.sin(a) * (.65 + i % 3 * .22)));
          }
        }
        case "CLOCK", "DRAGON_CREST" -> {
          for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            point(color, j.start.add(Math.cos(a) * 1.25, .04, Math.sin(a) * 1.25));
            if (i % 4 == 0)
              line(
                  color,
                  j.start.add(Math.cos(a) * .9, .04, Math.sin(a) * .9),
                  j.start.add(Math.cos(a) * 1.4, .04, Math.sin(a) * 1.4),
                  5);
          }
        }
        case "FIRE_SLASH", "PURPLE_SLASH", "DARK_SLASH", "MATRIX_SLASH" -> {
          Vec3d dir = j.end.subtract(j.start).normalize(), side = new Vec3d(dir.z, 0, -dir.x);
          for (int i = 0; i < 24; i++) {
            double a = (i / 23d - .5) * 2;
            point(
                p.phase().equals("PURPLE_SLASH") ? dust(0xbd53ff, .8f) : color,
                j.start
                    .add(0, 1, 0)
                    .add(dir.multiply(Math.cos(a) * 2))
                    .add(side.multiply(Math.sin(a) * 2)));
          }
        }
        case "FIRE_BOLT", "MATRIX_BOLT", "CARD_HIT", "DARK_RUSH", "DRAGON_CHARGE" ->
            line(p.phase().equals("FIRE_BOLT") ? ParticleTypes.FLAME : color, j.start, j.end, 12);
        case "FIRE_RAIN" -> {
          for (int i = 0; i < 8; i++)
            line(
                ParticleTypes.FLAME,
                j.start.add((i % 3 - 1) * .8, 0, (i / 3 - 1) * .8),
                j.end.add((i % 3 - 1) * .8, 0, (i / 3 - 1) * .8),
                5);
        }
        case "KICK_IMPACT" -> {
          if (j.age % 3 == 0) {
            point(ParticleTypes.EXPLOSION, j.start);
            for (int i = 0; i < 30; i++) {
              double a = i * Math.PI / 15;
              point(color, j.start.add(Math.cos(a) * t * 3, .1, Math.sin(a) * t * 3));
            }
          }
        }
        case "SPIRIT_1", "SPIRIT_2", "SPIRIT_3" -> {
          int n = Integer.parseInt(p.phase().substring(7));
          var c = dust(n == 1 ? 0xdaa92d : n == 2 ? 0xff3150 : 0xf56d14, .6f);
          for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            point(c, pos.add(Math.cos(a) * .3, 2.5 + Math.sin(a) * .3, 0));
          }
          line(c, pos.add(-.2, 2.2, 0), pos.add(.2, 2.8, 0), 8);
        }
        case "ULTIMATE_PHANTOM", "KNIGHT_PHANTOM", "DECADE_PHANTOM" -> {
          if (j.age % 3 == 0) line(color, j.start, j.start.add(0, 2, 0), 14);
        }
      }
      j.age++;
    }
  }

  private static DustParticleEffect dust(int rgb, float size) {
    return new DustParticleEffect(
        new Vector3f((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f), size);
  }

  private static void point(ParticleEffect effect, Vec3d at) {
    if (budget-- <= 0) return;
    world.addParticle(effect, at.x, at.y, at.z, 0, 0, 0);
    requests++;
  }

  private static void line(ParticleEffect effect, Vec3d a, Vec3d b, int n) {
    for (int i = 0; i < n; i++) point(effect, a.lerp(b, i / (double) Math.max(1, n - 1)));
  }

  private static void card(Vec3d at, Vec3d side, ParticleEffect color, double scale) {
    Vec3d right = side.multiply(scale * .45), up = new Vec3d(0, scale * .7, 0);
    line(color, at.subtract(right).subtract(up), at.add(right).subtract(up), 6);
    line(color, at.add(right).subtract(up), at.add(right).add(up), 8);
    line(color, at.add(right).add(up), at.subtract(right).add(up), 6);
    line(color, at.subtract(right).add(up), at.subtract(right).subtract(up), 8);
    for (int i = -2; i <= 2; i++) {
      Vec3d x = side.multiply(i * .075 * scale);
      line(color, at.add(x).add(0, -.35 * scale, 0), at.add(x).add(0, .35 * scale, 0), 5);
    }
  }

  static com.google.gson.JsonObject observation() {
    var out = new com.google.gson.JsonObject();
    out.add("phases", new com.google.gson.Gson().toJsonTree(OBSERVED));
    out.addProperty("minecraftParticleRequests", requests);
    out.addProperty("nativeCobblemonRequests", nativeRequests);
    out.addProperty("activeJobs", JOBS.size());
    return out;
  }
}
