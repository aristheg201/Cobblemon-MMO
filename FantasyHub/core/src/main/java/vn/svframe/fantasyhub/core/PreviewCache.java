package vn.svframe.fantasyhub.core;
import java.util.*;import java.util.function.Supplier;
/** Main-thread-only, bounded immutable preview values. Revision changes invalidate all entries. */
final class PreviewCache<K,V>{
 private long hits,misses,evictions;
 java.util.Map<String,Long> metrics(){return java.util.Map.of("hits",hits,"misses",misses,"evictions",evictions,"size",(long)values.size());}
 void resetMetrics(){hits=misses=evictions=0;}
 private final int bound;private long revision=Long.MIN_VALUE;private final LinkedHashMap<K,V> values=new LinkedHashMap<>();
 PreviewCache(int bound){this.bound=bound;}
 V get(long revision,K key,Supplier<V> factory){if(this.revision!=revision){values.clear();this.revision=revision;}if(values.containsKey(key)){hits++;return values.get(key);}misses++;var value=factory.get();if(values.size()>=bound){evictions++;values.remove(values.keySet().iterator().next());}values.put(key,value);return value;}
 void clear(){values.clear();}
}
