package vn.svframe.fantasyhub.core.rider;

/** Server-tick state. Survive/Complete have no expiration; recasts are finite and single use. */
public final class CardRiderState {
  public boolean transformed, elevated, nextCards;
  public long guardUntil,
      spiritUntil,
      illusionUntil,
      recastUntil,
      spiritSelectUntil,
      passiveReadyAt;
  public int selectedSpirit = 1, ultimateProjection;
  public double barrier;
  private int flame, spirit;

  public void henshin() {
    clear();
    transformed = true;
  }

  public boolean toggle() {
    if (!transformed) return false;
    elevated = !elevated;
    return elevated;
  }

  /** Visual stance only; Decade Complete always keeps the Decade armor. */
  public String visualForm(String rider) {
    if (!transformed) return "";
    return rider.equals("RYUKI") && elevated
        ? "ryuki_survive"
        : rider.toLowerCase(java.util.Locale.ROOT);
  }

  public boolean resonance() {
    return resonance(10);
  }

  public boolean resonance(int threshold) {
    if (!transformed || elevated) return false;
    if (++flame < threshold) return false;
    flame = 0;
    return true;
  }

  public int flame() {
    return flame;
  }

  public int cycleSpirit(long now, int duration) {
    spirit = spirit % 3 + 1;
    spiritUntil = now + duration;
    return spirit;
  }

  public int selectSpirit(long now, int duration, int selectionWindow) {
    if (now < spiritSelectUntil) selectedSpirit = selectedSpirit % 3 + 1;
    else spiritSelectUntil = now + selectionWindow;
    spirit = selectedSpirit;
    spiritUntil = now + duration;
    return spirit;
  }

  public boolean passive(long now, int cooldown) {
    if (now < passiveReadyAt) return false;
    passiveReadyAt = now + cooldown;
    return true;
  }

  public int spirit(long now) {
    return now < spiritUntil ? spirit : 0;
  }

  public boolean recast(long now) {
    if (now >= recastUntil) return false;
    recastUntil = 0;
    return true;
  }

  public boolean consumeCards() {
    boolean armed = nextCards;
    nextCards = false;
    return armed;
  }

  public double absorb(double damage) {
    double blocked = Math.min(barrier, damage);
    barrier -= blocked;
    return damage - blocked;
  }

  public void clear() {
    transformed = elevated = nextCards = false;
    flame = spirit = 0;
    barrier = 0;
    guardUntil = spiritUntil = illusionUntil = recastUntil = spiritSelectUntil = passiveReadyAt = 0;
  }
}
