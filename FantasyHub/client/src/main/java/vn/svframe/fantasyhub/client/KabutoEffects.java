package vn.svframe.fantasyhub.client;

import java.util.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.*;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Finite presentation timelines, started exclusively by server semantic events. */
final class KabutoEffects {
  private static final Set<String> PHASES =
      Set.of(
          "HENSHIN",
          "CAST_OFF",
          "LEAP",
          "LAND",
          "CLOCK",
          "CLOCK_END",
          "KICK",
          "KICK_HIT",
          "GUN_MUZZLE",
          "GUN",
          "GUN_HIT",
          "GUN_BLOCK",
          "SLASH",
          "BLEED",
          "EMPOWER",
          "BLOCK",
          "HYPER",
          "DASH",
          "MODE");
  private static final DustParticleEffect RED = dust(.98f, .035f, .065f, .8f),
      CYAN = dust(.08f, .8f, 1, .65f),
      SILVER = dust(.82f, .9f, 1, .65f),
      GOLD = dust(1, .64f, .045f, .85f);
  private static final List<Job> jobs = new ArrayList<>();
  private static final Map<String, Long> phases = new LinkedHashMap<>();
  private static ClientWorld world;
  private static int particleBudget, nativeBudget;
  private static long minecraftRequests, budgetDrops;

  private static final class Job {
    final Protocol.KabutoFx event;
    final Vec3d origin, end;
    int age;
    Vec3d last;

    Job(Protocol.KabutoFx p) {
      event = p;
      origin = new Vec3d(p.x(), p.y(), p.z());
      end = new Vec3d(p.tx(), p.ty(), p.tz());
      last = origin;
    }
  }

  private static DustParticleEffect dust(float r, float g, float b, float size) {
    return new DustParticleEffect(new Vector3f(r, g, b), size);
  }

  static void accept(Protocol.KabutoFx p) {
    var client = MinecraftClient.getInstance();
    if (client.world == null
        || !PHASES.contains(p.phase())
        || !finite(p.x(), p.y(), p.z(), p.tx(), p.ty(), p.tz(), p.radius(), p.density())
        || p.duration() < 1
        || p.duration() > 1200
        || p.radius() < 0
        || p.radius() > 32
        || p.density() < 0
        || p.density() > 1.5) return;
    if (client.world != world) {
      clear();
      world = client.world;
    }
    if (density() <= 0) return;
    Vec3d origin = new Vec3d(p.x(), p.y(), p.z()), end = new Vec3d(p.tx(), p.ty(), p.tz());
    if (client.gameRenderer.getCamera().getPos().squaredDistanceTo(origin) > 4096
        && client.gameRenderer.getCamera().getPos().squaredDistanceTo(end) > 4096) return;
    if (jobs.size() >= 32) {
      budgetDrops++;
      return;
    }
    if (p.phase().equals("LAND"))
      jobs.removeIf(j -> j.event.entityId() == p.entityId() && j.event.phase().equals("LEAP"));
    if (p.phase().equals("CLOCK_END"))
      jobs.removeIf(j -> j.event.entityId() == p.entityId() && j.event.phase().equals("CLOCK"));
    if (p.phase().equals("HENSHIN")) jobs.removeIf(j -> j.event.entityId() == p.entityId());
    jobs.add(new Job(p));
    phases.merge(p.phase(), 1L, Long::sum);
  }

  private static boolean finite(double... values) {
    for (double n : values) if (!Double.isFinite(n)) return false;
    return true;
  }

  private static float density() {
    return FantasyHubClient.options == null ? 1 : FantasyHubClient.options.effectsDensity;
  }

  static void tick() {
    var client = MinecraftClient.getInstance();
    if (client.world != world) {
      clear();
      world = client.world;
    }
    if (world == null) return;
    particleBudget = (int) (384 * density());
    nativeBudget = density() >= .75 ? 4 : density() > 0 ? 2 : 0;
    if (client.options.getParticles().getValue().name().equals("MINIMAL")) {
      particleBudget = Math.min(particleBudget, 96);
      nativeBudget = Math.min(nativeBudget, 1);
    } else if (client.options.getParticles().getValue().name().equals("DECREASED")) {
      particleBudget = Math.min(particleBudget, 192);
      nativeBudget = Math.min(nativeBudget, 2);
    }
    for (var it = jobs.iterator(); it.hasNext(); ) {
      var job = it.next();
      if (density() <= 0 || job.age >= job.event.duration()) {
        it.remove();
        continue;
      }
      Entity entity = world.getEntityById(job.event.entityId());
      if (Set.of("HENSHIN", "CLOCK", "HYPER", "EMPOWER", "BLOCK", "MODE")
              .contains(job.event.phase())
          && (entity == null || !entity.isAlive())) {
        it.remove();
        continue;
      }
      if (job.event.phase().equals("CLOCK")
          && job.age > 1
          && (KabutoVisuals.state(job.event.entityId()) == null
              || !KabutoVisuals.state(job.event.entityId()).clock())) {
        it.remove();
        continue;
      }
      render(job, entity);
      job.age++;
    }
    KabutoSnowstorm.tick();
  }

  private static void render(Job j, Entity entity) {
    var e = j.event;
    float t = j.age / (float) e.duration();
    Vec3d p = entity == null ? j.origin : entity.getPos(),
        forward =
            entity == null
                ? j.end.subtract(j.origin).normalize()
                : entity.getRotationVec(1).normalize();
    if (forward.lengthSquared() < .01) forward = new Vec3d(0, 0, 1);
    ParticleEffect charge = e.hyper() ? GOLD : CYAN;
    switch (e.phase()) {
      case "HENSHIN" -> {
        ring(j, p.add(0, .15 + t * 1.8, 0), .56 + Math.sin(t * Math.PI) * .12, SILVER, 24);
        for (int arm = 0; arm < 2; arm++) {
          double a = j.age * .55 + arm * Math.PI;
          point(j, RED, p.add(Math.cos(a) * .7, .15 + t * 1.8, Math.sin(a) * .7));
          point(j, CYAN, p.add(Math.cos(a + .2) * .68, .17 + t * 1.8, Math.sin(a + .2) * .68));
        }
        if (j.age % 4 == 0) ring(j, p.add(0, .08, 0), .7 + t * .6, RED, 24);
        if (j.age == 0) {
          nativeFx(j, "swordsdance_sparkle", p.add(0, .9, 0), forward);
          nativeFx(j, "metal_sparks", p.add(0, .9, 0), forward);
        }
      }
      case "CAST_OFF" -> {
        ring(j, j.origin.add(0, .08, 0), .6 + t * 3.4, RED, 36);
        ring(j, j.origin.add(0, .12, 0), .45 + t * 3.4, SILVER, 28);
        if (j.age < 6)
          for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Vec3d shard =
                j.origin.add(
                    Math.cos(a) * (.5 + t * 4),
                    .4 + (i % 3) * .45 + t * .4,
                    Math.sin(a) * (.5 + t * 4));
            point(
                j,
                new BlockStateParticleEffect(
                    ParticleTypes.BLOCK, Blocks.IRON_BLOCK.getDefaultState()),
                shard,
                Math.cos(a) * .1,
                .13,
                Math.sin(a) * .1);
          }
        if (j.age == 0) {
          nativeFx(j, "explosion_burst_flash", j.origin.add(0, .8, 0), forward);
          nativeFx(j, "impact_steel", j.origin.add(0, 1, 0), forward);
          nativeFx(j, "metal_sparks", j.origin.add(0, 1.3, 0), forward);
        }
      }
      case "LAND" -> {
        ring(j, j.origin.add(0, .07, 0), Math.max(.2, e.radius() * t), RED, 40);
        ring(j, j.origin.add(0, .12, 0), Math.max(.15, e.radius() * t - .2), SILVER, 32);
        if (j.age == 0) {
          nativeFx(j, "bulletpunch_targetsparks", j.origin.add(0, .3, 0), new Vec3d(0, 1, 0));
          nativeFx(j, "explosion_burst_flash", j.origin.add(0, .1, 0), forward);
        }
        if (j.age < 4)
          for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            point(
                j,
                ParticleTypes.POOF,
                j.origin.add(Math.cos(a) * (.4 + t * 3), .12, Math.sin(a) * (.4 + t * 3)),
                Math.cos(a) * .12,
                .04,
                Math.sin(a) * .12);
          }
      }
      case "LEAP" -> {
        if (j.age % 2 == 0) {
          ring(j, j.end.add(0, .08, 0), .95 + Math.sin(j.age * .15) * .15, RED, 24);
          if (entity != null)
            for (int i = 0; i < 3; i++)
              point(
                  j,
                  SILVER,
                  p.add(Math.sin(j.age + i) * .2, .1 + i * .2, Math.cos(j.age + i) * .2));
        }
      }
      case "CLOCK" -> {
        if (j.age < 10) {
          ring(j, p.add(0, .08, 0), e.radius() * (j.age + 1) / 10d, charge, 40);
        }
        if (j.age % 3 == 0) {
          ring(j, p.add(0, .12, 0), .5, charge, 12);
          double distance = j.last.distanceTo(p);
          if (distance > .02 && distance < 10)
            line(
                j,
                charge,
                j.last.add(0, .65, 0),
                p.add(0, .65, 0),
                Math.min(18, 4 + (int) (distance * 6)));
          for (int i = 0; i < 6; i++) {
            double a = j.age * .7 + i * Math.PI / 3;
            point(
                j,
                ParticleTypes.ELECTRIC_SPARK,
                p.add(Math.cos(a) * .48, .3 + (i % 3) * .48, Math.sin(a) * .48),
                0,
                .015,
                0);
          }
        }
        if (j.age % 10 == 0) nativeFx(j, "quickattack_dashlines", p.add(0, .65, 0), forward);
        if (j.age == 0) nativeFx(j, "swordsdance_sparkle", p.add(0, .6, 0), forward);
        j.last = p;
      }
      case "CLOCK_END" -> {
        if (j.age % 2 == 0) ring(j, j.origin.add(0, .15, 0), 1.3 * (1 - t), SILVER, 24);
        if (j.age == 0) nativeFx(j, "metal_sparks", j.origin.add(0, .8, 0), forward);
      }
      case "KICK", "DASH" -> {
        Vec3d delta = j.end.subtract(j.origin), side = new Vec3d(-delta.z, 0, delta.x).normalize();
        if (j.age < 8)
          for (int i = 0; i < 24; i++) {
            double f = i / 23d, a = f * 18 + j.age * .7;
            Vec3d at = j.origin.lerp(j.end, f).add(0, .75, 0);
            point(
                j,
                e.hyper() ? GOLD : RED,
                at.add(side.multiply(Math.sin(a) * .18)).add(0, Math.cos(a) * .18, 0));
            if ((i & 1) == 0) point(j, SILVER, at);
          }
        if (j.age == 0) {
          nativeFx(j, "quickattack_dashlines", j.origin.add(0, .7, 0), delta);
          nativeFx(j, "thundershock_actorbolts", j.end.add(0, .5, 0), delta);
        }
      }
      case "KICK_HIT" -> {
        if (j.age < 5)
          for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            line(
                j,
                e.hyper() ? GOLD : CYAN,
                j.origin,
                j.origin.add(
                    Math.cos(a) * (1 + t * 2), Math.sin(a) * (1 + t * 2), Math.sin(a * .5) * .3),
                4);
          }
        if (j.age == 0) {
          nativeFx(j, "thunderbolt_targetimpact", j.origin, forward);
          nativeFx(j, "impact_electric", j.origin, forward);
        }
      }
      case "GUN" -> {
        if (j.age < 3) {
          line(j, CYAN, j.origin, j.end, Math.clamp((int) (j.origin.distanceTo(j.end) * 5), 6, 32));
          point(j, ParticleTypes.ELECTRIC_SPARK, j.origin, 0, .01, 0);
        }
      }
      case "GUN_MUZZLE" -> {
        if (j.age < 2)
          for (int i = 0; i < 6; i++)
            point(j, SILVER, j.origin.add(Math.sin(i) * .08, Math.cos(i) * .08, 0));
        if (j.age == 0) nativeFx(j, "metal_sparks", j.origin, j.end.subtract(j.origin));
      }
      case "GUN_HIT", "GUN_BLOCK" -> {
        if (j.age < 4) ring(j, j.origin, .15 + t * .65, CYAN, 12);
        if (j.age == 0) {
          nativeFx(j, "bulletpunch_targetsparks", j.origin, j.end.subtract(j.origin));
          if (e.phase().equals("GUN_BLOCK")) nativeFx(j, "metal_sparks", j.origin, forward);
        }
      }
      case "SLASH" -> {
        Vec3d dir = j.end.subtract(j.origin).normalize(),
            side = new Vec3d(-dir.z, 0, dir.x).normalize();
        if (j.age < 6)
          for (int i = 0; i < 18; i++) {
            double a = -1.4 + i * 2.8 / 17;
            Vec3d at = j.end.add(side.multiply(Math.sin(a) * .85)).add(0, Math.cos(a) * .7 - .3, 0);
            point(j, RED, at);
            point(j, SILVER, at.add(0, .06, 0));
          }
        if (j.age == 0) {
          nativeFx(j, "scratch_target", j.end, dir);
          nativeFx(j, "scratch_targetsparks", j.end, dir);
        }
      }
      case "BLEED" -> {
        if (j.age < 3)
          for (int i = 0; i < 3; i++)
            point(
                j,
                RED,
                j.origin.add(Math.sin(j.age + i) * .18, i * .08, Math.cos(j.age + i) * .18),
                0,
                -.03,
                0);
      }
      case "HYPER" -> {
        if (j.age % 2 == 0) {
          ring(j, p.add(0, .1 + t * 1.8, 0), .55 + Math.sin(t * Math.PI) * .4, GOLD, 28);
          ring(j, p.add(0, 1.8 - t * 1.7, 0), .65, CYAN, 24);
        }
        if (j.age == 0) {
          nativeFx(j, "swordsdance_sparkle", p.add(0, 1, 0), forward);
          nativeFx(j, "thundershock_actorbolts", p.add(0, .7, 0), forward);
        }
      }
      case "EMPOWER", "BLOCK", "MODE" -> {
        if (j.age % 2 == 0)
          ring(j, p.add(0, .9, 0), .35 + t * .6, e.phase().equals("EMPOWER") ? GOLD : SILVER, 18);
        if (j.age == 0)
          nativeFx(
              j,
              e.phase().equals("BLOCK") ? "impact_steel" : "swordsdance_sparkle",
              p.add(0, .9, 0),
              forward);
      }
    }
  }

  private static void point(Job j, ParticleEffect effect, Vec3d pos) {
    point(j, effect, pos, 0, 0, 0);
  }

  private static void point(Job j, ParticleEffect effect, Vec3d pos, double x, double y, double z) {
    if ((j.event.backends() & 1) == 0) return;
    if (particleBudget <= 0) {
      budgetDrops++;
      return;
    }
    double d = Math.min(1.5, density() * j.event.density());
    // Deterministic thinning keeps low quality from changing any gameplay state.
    if (d < 1 && ((minecraftRequests + budgetDrops) % 10) >= Math.round(d * 10)) {
      budgetDrops++;
      return;
    }
    world.addParticle(effect, pos.x, pos.y, pos.z, x, y, z);
    particleBudget--;
    minecraftRequests++;
  }

  private static void ring(Job j, Vec3d p, double radius, ParticleEffect type, int count) {
    count = (int) Math.round(count * Math.max(1, Math.min(1.5, density() * j.event.density())));
    for (int i = 0; i < count; i++) {
      double a = i * Math.PI * 2 / count;
      point(j, type, p.add(Math.cos(a) * radius, 0, Math.sin(a) * radius));
    }
  }

  private static void line(Job j, ParticleEffect p, Vec3d a, Vec3d b, int count) {
    count = (int) Math.round(count * Math.max(1, Math.min(1.5, density() * j.event.density())));
    for (int i = 0; i < count; i++) point(j, p, a.lerp(b, i / (double) Math.max(1, count - 1)));
  }

  private static void nativeFx(Job j, String name, Vec3d p, Vec3d direction) {
    if ((j.event.backends() & 2) == 0
        || FantasyHubClient.options != null && !FantasyHubClient.options.cobblemonEffects) return;
    if (nativeBudget <= 0) {
      budgetDrops++;
      return;
    }
    nativeBudget--;
    KabutoSnowstorm.emit(name, p, direction);
  }

  static com.google.gson.JsonObject observation() {
    var j = new com.google.gson.JsonObject();
    j.add("phases", new com.google.gson.Gson().toJsonTree(phases));
    j.addProperty("minecraftParticleRequests", minecraftRequests);
    j.addProperty("activeTimelines", jobs.size());
    j.addProperty("budgetDrops", budgetDrops);
    j.addProperty("density", density());
    j.add("cobblemon", new com.google.gson.Gson().toJsonTree(KabutoSnowstorm.observation()));
    return j;
  }

  static void removeEntity(int id) {
    jobs.removeIf(j -> j.event.entityId() == id);
  }

  static void clear() {
    jobs.clear();
    phases.clear();
    minecraftRequests = budgetDrops = 0;
    KabutoSnowstorm.clear();
    world = null;
  }
}
