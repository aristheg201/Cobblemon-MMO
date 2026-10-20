package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded main-thread action queue. Stable ordering and owner cancellation prevent ghost hits. */
public final class HeroTimeline {
  private record Step(UUID owner,long tick,long sequence,BooleanSupplier valid,Runnable action) {}
  private final PriorityQueue<Step> steps=new PriorityQueue<>(Comparator.comparingLong(Step::tick).thenComparingLong(Step::sequence));
  private final Map<UUID,Integer> counts=new HashMap<>();
  private long sequence;
  public boolean schedule(UUID owner,long tick,BooleanSupplier valid,Runnable action) {
    Objects.requireNonNull(owner);Objects.requireNonNull(valid);Objects.requireNonNull(action);
    int count=counts.getOrDefault(owner,0);if(count>=128)return false;
    steps.add(new Step(owner,tick,sequence++,valid,action));counts.put(owner,count+1);return true;
  }
  public void tick(long tick) {
    // Actions may schedule later work, but no tick can execute an unbounded self-rescheduling loop.
    int remaining=steps.size();
    while(remaining-->0&&!steps.isEmpty()&&steps.peek().tick<=tick) {
      var step=steps.remove();counts.computeIfPresent(step.owner,(id,n)->n<=1?null:n-1);
      if(step.valid.getAsBoolean())step.action.run();
    }
  }
  public void cancel(UUID owner){steps.removeIf(s->s.owner.equals(owner));counts.remove(owner);}
  public int pending(){return steps.size();}
  public void clear(){steps.clear();counts.clear();}
}
