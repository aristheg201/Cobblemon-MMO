package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import vn.svframe.fantasyhub.protocol.Protocol;

/** Small, finite, local camera response to nearby authoritative impacts. Configurable to zero. */
public final class RiderCamera {
  private static long until;

  static void impact(Protocol.RiderFx packet) {
    var client = MinecraftClient.getInstance();
    if (client.world == null || client.player == null || !packet.phase().equals("KICK_IMPACT"))
      return;
    if (client.player.squaredDistanceTo(packet.x(), packet.y(), packet.z()) <= 100)
      until = client.world.getTime() + 7;
  }

  static void clear() {
    until = 0;
  }

  public static void render(MatrixStack matrices, float delta) {
    var client = MinecraftClient.getInstance();
    if (client.world == null || FantasyHubClient.options == null || until <= client.world.getTime())
      return;
    float time = until - client.world.getTime() - delta;
    float strength = FantasyHubClient.options.cameraShakeStrength * time / 7;
    matrices.multiply(
        RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(time * 2.7) * strength));
    matrices.multiply(
        RotationAxis.POSITIVE_X.rotationDegrees((float) Math.cos(time * 2.1) * strength * .45f));
  }
}
