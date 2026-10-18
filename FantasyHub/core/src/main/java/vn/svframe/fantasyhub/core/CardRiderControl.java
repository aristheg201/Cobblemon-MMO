package vn.svframe.fantasyhub.core;

public final class CardRiderControl {
  private CardRiderControl() {}

  public static boolean stunned(net.minecraft.entity.LivingEntity e) {
    return CardRiderSkills.stunned(e);
  }

  public static float healing(net.minecraft.entity.LivingEntity e, float amount) {
    return CardRiderSkills.healing(e, amount);
  }
}
