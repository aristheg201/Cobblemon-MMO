package vn.svframe.svframemmo.cobblemon.gameplay;

/** Claiming AI ownership is distinct from permission to perform an attack right now. */
public final class HostilityRules {
  private HostilityRules() {}

  public static boolean claims(
      boolean enabled, boolean owned, boolean clone, boolean encounter, boolean excluded) {
    return enabled && !owned && !clone && !encounter && !excluded;
  }

  public static boolean canAttack(
      boolean alive,
      boolean battle,
      boolean busy,
      boolean fainted,
      boolean capturing,
      boolean invalidPlayer) {
    return alive && !battle && !busy && !fainted && !capturing && !invalidPlayer;
  }
}
