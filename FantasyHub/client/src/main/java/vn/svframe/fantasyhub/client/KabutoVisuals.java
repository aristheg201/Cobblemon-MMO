package vn.svframe.fantasyhub.client;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Rendering consumes confirmed semantic state; it never advances resources locally. */
public final class KabutoVisuals {
  private record Pose(String ability, float age, int duration) {}

  private static final Map<Integer, Pose> POSES = new HashMap<>();

  static void cue(Protocol.KabutoCue cue) {
    var client = MinecraftClient.getInstance();
    var entity = client.world == null ? null : client.world.getEntityById(cue.entityId());
    if (entity != null)
      POSES.put(
          entity.getId(), new Pose(cue.ability(), entity.age, Math.clamp(cue.duration(), 1, 100)));
  }

  public static void animate(
      net.minecraft.client.render.entity.model.PlayerEntityModel<?> model,
      net.minecraft.entity.LivingEntity entity,
      float age) {
    if (STATES.containsKey(entity.getId())) {
      model.hat.visible =
          model.jacket.visible =
              model.leftSleeve.visible =
                  model.rightSleeve.visible =
                      model.leftPants.visible = model.rightPants.visible = false;
    }
    Pose pose = POSES.get(entity.getId());
    if (pose == null) return;
    float t = (age - pose.age) / pose.duration;
    if (t < 0 || t >= 1) {
      POSES.remove(entity.getId());
      return;
    }
    float strength = (float) Math.sin(Math.PI * t);
    switch (pose.ability) {
      case "GUN_FIRE" -> {
        // The confirmed cast and synchronized mode also work for remote Polymer stacks,
        // whose private server item NBT need not be visible to an observing client.
        var arm =
            entity.getMainArm() == net.minecraft.util.Arm.LEFT ? model.leftArm : model.rightArm;
        float aim = t < .65f ? 1 : (1 - t) / .35f;
        arm.pitch = (-1.5708f + entity.getPitch() * .0174533f) * aim;
        arm.yaw = entity.getMainArm() == net.minecraft.util.Arm.LEFT ? .1f : -.1f;
        arm.roll = 0;
      }
      case "RIDER_KICK" -> {
        model.rightLeg.pitch = -1.65f * strength;
        model.leftLeg.pitch = .3f * strength;
        model.body.pitch = .18f * strength;
        model.rightArm.pitch = .65f * strength;
        model.leftArm.pitch = .65f * strength;
      }
      case "HENSHIN" -> {
        model.rightArm.pitch = -1.1f * strength;
        model.rightArm.yaw = -.8f * strength;
        model.leftArm.pitch = -.8f * strength;
        model.leftArm.yaw = .65f * strength;
      }
      case "CAST_OFF", "HYPER_FORM" -> {
        model.rightArm.roll = -1.15f * strength;
        model.leftArm.roll = 1.15f * strength;
        model.rightLeg.pitch = .25f * strength;
        model.leftLeg.pitch = -.25f * strength;
      }
      default -> {
        return;
      }
    }
    model.rightSleeve.copyTransform(model.rightArm);
    model.leftSleeve.copyTransform(model.leftArm);
    model.rightPants.copyTransform(model.rightLeg);
    model.leftPants.copyTransform(model.leftLeg);
    model.jacket.copyTransform(model.body);
    model.hat.copyTransform(model.head);
  }

  private static final Map<Integer, Protocol.Kabuto> STATES = new HashMap<>();

  static void accept(Protocol.Kabuto state) {
    if (state.form() < 0) {
      STATES.remove(state.entityId());
      KabutoEffects.removeEntity(state.entityId());
    } else STATES.put(state.entityId(), state);
  }

  public static Protocol.Kabuto state(int entityId) {
    return STATES.get(entityId);
  }

  static void remove(int id) {
    STATES.remove(id);
    POSES.remove(id);
  }

  static void clear() {
    STATES.clear();
    POSES.clear();
  }

  static void hud(DrawContext c) {
    var client = MinecraftClient.getInstance();
    if (client.player == null) return;
    var s = state(client.player.getId());
    if (s == null) return;
    if (SkillBarPresentation.slots(FantasyHubClient.state()).isEmpty() && !s.clock()) return;
    int x = c.getScaledWindowWidth() / 2 - 91, y = c.getScaledWindowHeight() - 105;
    // Two compact gauges sit directly above the skill row, away from thirst.
    int color = s.hyper() ? 0xffffca43 : 0xffdc2432;
    c.fill(x, y, x + 81, y + 3, 0xff29232a);
    c.fill(x, y, x + s.energy() * 81 / 100, y + 3, color);
    c.fill(x + 101, y, x + 182, y + 3, 0xff29232a);
    c.fill(
        x + 101,
        y,
        x + 101 + (s.clock() ? Math.clamp(s.clockTicks(), 0, 100) : s.gauge()) * 81 / 100,
        y + 3,
        0xff34cef5);
    c.getMatrices().push();
    c.getMatrices().translate(x, y - 7, 0);
    c.getMatrices().scale(.65f, .65f, 1);
    c.drawText(
        client.textRenderer,
        Text.translatable("fantasyhub.kabuto.energy", s.energy()),
        0,
        0,
        0xffeee1d0,
        true);
    c.drawText(
        client.textRenderer,
        Text.translatable(
            s.clock() ? "fantasyhub.kabuto.clock_active" : "fantasyhub.kabuto.gauge",
            s.clock()
                ? String.format(java.util.Locale.ROOT, "%.1fs", s.clockTicks() / 20d)
                : String.valueOf(s.gauge())),
        155,
        0,
        0xffb7f7ff,
        true);
    c.getMatrices().pop();
  }
}
