package vn.svframe.fantasyhub.core.rider;

import java.util.function.DoublePredicate;

/** Ordered body-volume probes cannot skip an occupied middle sample for a clear endpoint. */
public final class SweptPath {
  private SweptPath() {}

  public static double clearFraction(double distance, DoublePredicate clearAt) {
    if (!Double.isFinite(distance) || distance < 0 || distance > 64) return 0;
    int steps = Math.max(1, (int) Math.ceil(distance / .125));
    double last = 0;
    for (int step = 1; step <= steps; step++) {
      double fraction = step / (double) steps;
      if (!clearAt.test(fraction)) break;
      last = fraction;
    }
    return last;
  }
}
