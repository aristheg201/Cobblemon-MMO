package vn.svframe.fantasyhub.core.hero;

import java.util.*;

/** Runtime-only stacks, action leases and one-use recast windows; no duplicate player persistence. */
public final class HeroPowerState {
  private final Map<String,ArrayDeque<Long>> stacks=new HashMap<>();
  private final Map<String,Long> flags=new HashMap<>(),recasts=new HashMap<>();
  private int generation;
  public int generation(){return generation;}
  public int gain(String key,int maximum,int duration,long tick) {
    if(maximum<1||maximum>100||duration<0)throw new IllegalArgumentException("Invalid stack bounds");
    stacks(key,tick);var queue=stacks.computeIfAbsent(key,k->new ArrayDeque<>());
    if(queue.size()>=maximum)queue.removeFirst();queue.addLast(duration==0?Long.MAX_VALUE:tick+duration);return queue.size();
  }
  public int stacks(String key,long tick) {
    var queue=stacks.get(key);if(queue==null)return 0;
    while(!queue.isEmpty()&&queue.peekFirst()<=tick)queue.removeFirst();return queue.size();
  }
  public int take(String key,long tick){int n=stacks(key,tick);stacks.remove(key);return n;}
  public void flag(String key,int duration,long tick){if(duration<0)throw new IllegalArgumentException("Negative lease");flags.put(key,duration==0?Long.MAX_VALUE:tick+duration);}
  public void unset(String key){flags.remove(key);}
  public boolean active(String key,long tick){return flags.getOrDefault(key,Long.MIN_VALUE)>tick;}
  public long remaining(String key,long tick){long until=flags.getOrDefault(key,tick);return until==Long.MAX_VALUE?Long.MAX_VALUE:Math.max(0,until-tick);}
  public void openRecast(String skill,int duration,long tick){if(duration<1)throw new IllegalArgumentException("Empty recast window");recasts.put(skill,tick+duration);}
  public boolean canRecast(String skill,long tick){return recasts.getOrDefault(skill,Long.MIN_VALUE)>tick;}
  public boolean recast(String skill,long tick){boolean ok=canRecast(skill,tick);recasts.remove(skill);return ok;}
  public void clear(){stacks.clear();flags.clear();recasts.clear();generation++;}
}
