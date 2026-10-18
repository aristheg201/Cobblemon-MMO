package vn.svframe.fantasyhub.core.rider;

/** Server-tick state. Survive/Complete have no expiration; recasts are finite and single use. */
public final class CardRiderState {
  public boolean transformed, elevated, nextCards;
  public long guardUntil, spiritUntil, illusionUntil, recastUntil;
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

  public boolean resonance() {
    if (!transformed || elevated) return false;
    if (++flame < 10) return false;
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
    guardUntil = spiritUntil = illusionUntil = recastUntil = 0;
  }
}
