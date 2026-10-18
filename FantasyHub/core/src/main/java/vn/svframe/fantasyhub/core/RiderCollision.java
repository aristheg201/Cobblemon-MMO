package vn.svframe.fantasyhub.core;

import java.util.Optional;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Vanilla's surface raycast returns empty when the segment starts inside the box. */
final class RiderCollision {
  static Optional<Vec3d> contact(Box box, Vec3d from, Vec3d to) {
    return box.contains(from) ? Optional.of(from) : box.raycast(from, to);
  }
}
