package vn.svframe.fantasyhub.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Short additive overlays after vanilla locomotion. No bone state is retained between frames. */
public final class RiderAnimator {
  private record Motion(String pose, long start, int duration) {}

  private static final Map<Integer, Motion> ACTIVE = new HashMap<>();
  private static final Set<String> POSES =
      Set.of(
          "POSE_SWORD",
          "POSE_PUNCH",
          "POSE_GUN",
          "POSE_CAST",
          "POSE_BLOCK",
          "POSE_KICK",
          "POSE_SHIFT",
          "POSE_SPIN");

  static boolean accept(Protocol.RiderFx p) {
    if (!POSES.contains(p.phase())) return false;
    var world = MinecraftClient.getInstance().world;
    if (world != null && p.duration() > 0 && p.duration() <= 1200)
      ACTIVE.put(p.entityId(), new Motion(p.phase(), world.getTime(), p.duration()));
    return true;
  }

  static boolean posing(int entity, String pose) {
    var world = MinecraftClient.getInstance().world;
    var motion = ACTIVE.get(entity);
    return world != null
        && motion != null
        && motion.pose.equals("POSE_" + pose)
        && world.getTime() - motion.start < motion.duration;
  }

  static void tick() {
    var world = MinecraftClient.getInstance().world;
    ACTIVE
        .entrySet()
        .removeIf(
            e ->
                world == null
                    || world.getEntityById(e.getKey()) == null
                    || RiderVisuals.state(e.getKey()) == null
                    || world.getTime() - e.getValue().start >= e.getValue().duration);
  }

  static void clear() {
    ACTIVE.clear();
  }

  public static void animate(PlayerEntityModel<?> model, LivingEntity entity, float age) {
    var world = MinecraftClient.getInstance().world;
    if (world == null || RiderVisuals.state(entity.getId()) == null) {
      ACTIVE.remove(entity.getId());
      return;
    }
    var motion = ACTIVE.get(entity.getId());
    if (motion == null) return;
    double time = world.getTime() - motion.start + age - (float) Math.floor(age);
    if (time < 0 || time >= motion.duration) {
      ACTIVE.remove(entity.getId());
      return;
    }
    float t = (float) (time / motion.duration);
    float weight = Math.min(1, t * 7) * Math.min(1, (1 - t) * 6);
    float swing = (float) Math.sin(Math.PI * t);
    switch (motion.pose) {
      case "POSE_SWORD" -> {
        model.body.yaw += weight * (.35f - .7f * t);
        model.rightArm.pitch = lerp(weight, model.rightArm.pitch, -1.8f + swing * 1.5f);
        model.rightArm.yaw -= weight * (.9f - 1.8f * t);
        model.rightArm.roll -= weight * .45f;
        if (RiderVisuals.state(entity.getId()).form().equals("knight")
            && RiderVisuals.state(entity.getId()).force() == 1) {
          model.leftArm.pitch = lerp(weight, model.leftArm.pitch, -1.5f + swing);
          model.leftArm.yaw += weight * (.8f - 1.6f * t);
        }
      }
      case "POSE_PUNCH" -> {
        model.rightArm.pitch = lerp(weight, model.rightArm.pitch, -1.57f);
        model.leftArm.pitch = lerp(weight, model.leftArm.pitch, -.8f);
        model.body.yaw += .3f * weight;
      }
      case "POSE_GUN" -> {
        model.rightArm.pitch = lerp(weight, model.rightArm.pitch, -1.57f + model.head.pitch);
        model.rightArm.yaw = lerp(weight, model.rightArm.yaw, model.head.yaw - .05f);
        model.leftArm.pitch = lerp(weight, model.leftArm.pitch, -1.1f);
        model.leftArm.yaw += weight * .55f;
      }
      case "POSE_BLOCK" -> {
        model.rightArm.pitch = lerp(weight, model.rightArm.pitch, -1.2f);
        model.leftArm.pitch = lerp(weight, model.leftArm.pitch, -1.2f);
        model.rightArm.yaw -= weight * .7f;
        model.leftArm.yaw += weight * .7f;
      }
      case "POSE_KICK" -> {
        model.body.pitch += weight * .22f;
        model.rightLeg.pitch = lerp(weight, model.rightLeg.pitch, -1.35f);
        model.leftLeg.pitch = lerp(weight, model.leftLeg.pitch, .6f);
        model.rightArm.pitch = lerp(weight, model.rightArm.pitch, -.6f);
        model.leftArm.pitch = lerp(weight, model.leftArm.pitch, -.6f);
        model.leftArm.roll += weight * .55f;
        model.rightArm.roll -= weight * .55f;
      }
      case "POSE_SPIN" -> {
        model.body.yaw += weight * (float) Math.sin(t * Math.PI * 4) * .8f;
        model.leftArm.roll += weight * 1.25f;
        model.rightArm.roll -= weight * 1.25f;
        model.rightArm.pitch -= weight * .45f;
      }
      case "POSE_SHIFT", "POSE_CAST" -> {
        model.leftArm.pitch = lerp(weight, model.leftArm.pitch, -1.2f);
        model.rightArm.pitch = lerp(weight, model.rightArm.pitch, -.7f - swing * .6f);
        model.leftArm.yaw += weight * .55f;
        model.rightArm.yaw -= weight * .55f;
        model.head.pitch += weight * .15f;
      }
    }
  }

  private static float lerp(float weight, float from, float to) {
    return from + (to - from) * weight;
  }
}
