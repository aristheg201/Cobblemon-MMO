package vn.svframe.fantasyhub.core.hero;
import java.util.*;

/** One target category across attackers: stun/root/freeze/air-lock cannot bypass each other. */
public final class HeroControlDiminishingReturns {
  private record Chain(long last,int count) {}
  private final Map<UUID,Chain> chains=new HashMap<>();
  private final int cap,window;
  public HeroControlDiminishingReturns(){this(30,200);}
  public HeroControlDiminishingReturns(int cap,int window){
    if(cap<0||window<1)throw new IllegalArgumentException("Invalid control rules");
    this.cap=cap;this.window=window;
  }
  public int duration(UUID target,long tick,int requested,boolean player) {
    if(requested<0)throw new IllegalArgumentException("Negative control duration");
    if(!player)return requested;
    var old=chains.get(target);int count=old==null||tick-old.last>window?0:old.count;
    if(requested==0)return 0;
    int ticks=count>=3?0:(int)(Math.min(cap,requested)*Math.pow(.5,count));
    // Repeated immune attempts do not extend the window indefinitely.
    if(ticks>0)chains.put(target,new Chain(tick,count+1));
    return ticks;
  }
  public void remove(UUID target){chains.remove(target);}
  public void expire(long tick){chains.entrySet().removeIf(e->tick-e.getValue().last>window);}
}
