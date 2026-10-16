package vn.svframe.fantasyhub.core;

/** Deterministic class state. Temporary speed never survives death/disconnect/class changes. */
final class KabutoState {
  enum Form {
    MASKED,
    RIDER
  }

  Form form = Form.MASKED;
  int energy, gauge, combo;
  boolean hyper;
  boolean transformed;
  boolean gun = true;
  long clockUntil;

  boolean deactivate() {
    if (!transformed) return false;
    transformed = false;
    form = Form.MASKED;
    hyper = false;
    clockUntil = 0;
    combo = 0;
    return true;
  }

  void gain(int energyAmount, int gaugeAmount) {
    energy = Math.clamp(energy + energyAmount, 0, 100);
    gauge = Math.clamp(gauge + gaugeAmount, 0, 100);
  }

  boolean consumeEmpowerment() {
    if (energy < 100) return false;
    energy = 0;
    combo = Math.min(10, combo + 2);
    return true;
  }

  boolean clock(long now) {
    return clockUntil > now;
  }

  boolean startClock(long now, int cost, int duration) {
    if (form != Form.RIDER || clock(now) || gauge < cost) return false;
    gauge -= cost;
    clockUntil = now + duration;
    return true;
  }
}
