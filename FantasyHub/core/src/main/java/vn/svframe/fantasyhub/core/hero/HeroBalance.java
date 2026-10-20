package vn.svframe.fantasyhub.core.hero;

public final class HeroBalance {
  private HeroBalance() {}
  public record Rules(double pvpMultiplier,double ultimateAttackBudget) {
    public Rules {
      if(!Double.isFinite(pvpMultiplier)||pvpMultiplier<0||pvpMultiplier>1||!Double.isFinite(ultimateAttackBudget)||ultimateAttackBudget<0||ultimateAttackBudget>20)
        throw new IllegalArgumentException("Invalid PvP damage rules");
    }
    public static Rules from(HeroCatalog catalog) {return new Rules(catalog.setting("pvp_multiplier"),catalog.setting("ultimate_attack_budget"));}
  }
  public static double coefficient(double phase,double activationTotal,boolean ultimate,boolean pvp) {
    return coefficient(phase,activationTotal,ultimate,pvp,new Rules(.65,3));
  }
  public static double coefficient(double phase,double activationTotal,boolean ultimate,boolean pvp,Rules rules) {
    if(!Double.isFinite(phase)||phase<0||!Double.isFinite(activationTotal)||activationTotal<0)
      throw new IllegalArgumentException("Damage coefficients must be finite and nonnegative");
    if(!pvp)return phase;
    return phase*(ultimate?Math.min(1,activationTotal==0?1:rules.ultimateAttackBudget/activationTotal):rules.pvpMultiplier);
  }
}
