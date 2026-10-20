package vn.svframe.fantasyhub.core.hero;

import java.util.*;

/** Main-thread, per-activation ledger shared by all waves/recasts/damage-over-time. */
public final class HeroCastBudget {
  private record Entry(double ceiling,double applied) {}
  private final double fraction;
  private final Map<UUID,Entry> targets=new HashMap<>();
  public HeroCastBudget(double fraction) {
    if(!Double.isFinite(fraction)||fraction<0||fraction>1)throw new IllegalArgumentException("HP cap must be 0..1");
    this.fraction=fraction;
  }
  public double allowed(UUID target,double maximumHp,double requested) {
    Objects.requireNonNull(target);
    if(!Double.isFinite(maximumHp)||maximumHp<=0||!Double.isFinite(requested)||requested<0)throw new IllegalArgumentException("Invalid damage or maximum HP");
    var e=targets.computeIfAbsent(target,k->new Entry(maximumHp*fraction,0));
    return Math.max(0,Math.min(requested,e.ceiling-e.applied));
  }
  public void record(UUID target,double applied) {
    if(!Double.isFinite(applied)||applied<0)throw new IllegalArgumentException("Invalid applied damage");
    var e=targets.get(target);
    if(e==null)throw new IllegalStateException("Damage must be authorized before recording");
    targets.put(target,new Entry(e.ceiling,e.applied+applied));
  }
}
