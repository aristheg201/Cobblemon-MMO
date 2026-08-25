package vn.svframe.mythiclibfabric.runtime.skill;
import java.util.*;
public final class CooldownMap {
 private final Map<String,Long> until=new HashMap<>();
 public synchronized boolean ready(String key,long now){return now>=until.getOrDefault(norm(key),0L);} 
 public synchronized long remaining(String key,long now){return Math.max(0,until.getOrDefault(norm(key),0L)-now);} 
 public synchronized void trigger(String key,long now,long durationMs){if(durationMs<0)throw new IllegalArgumentException(); until.put(norm(key),Math.addExact(now,durationMs));}
 public synchronized void clear(String key){until.remove(norm(key));}
 public synchronized void purge(long now){until.entrySet().removeIf(e->e.getValue()<=now);} 
 private static String norm(String s){s=Objects.requireNonNull(s).trim().toLowerCase(Locale.ROOT);if(s.isEmpty())throw new IllegalArgumentException();return s;}
}
