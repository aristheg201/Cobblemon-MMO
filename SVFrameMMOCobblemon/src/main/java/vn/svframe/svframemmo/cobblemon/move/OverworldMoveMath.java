package vn.svframe.svframemmo.cobblemon.move;

public final class OverworldMoveMath {
   private OverworldMoveMath() {
   }

   public static double healing(double maxHealth, double fraction, double configured, double baseline) {
      if (fraction <= 0.0) {
         return Math.max(0.0, configured > 0.0 ? configured : baseline);
      } else {
         double progression = baseline > 0.0 && configured > 0.0 ? configured / baseline : 1.0;
         return Math.max(0.0, Math.min(maxHealth, maxHealth * fraction * progression));
      }
   }
}
