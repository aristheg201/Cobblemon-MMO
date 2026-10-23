package vn.svframe.fantasyhub.core;
import java.util.*;import java.util.function.Supplier;
/** Main-thread-only, bounded immutable preview values. Revision changes invalidate all entries. */
final class PreviewCache<K,V>{
 private final int bound;private long revision=Long.MIN_VALUE;private final LinkedHashMap<K,V> values=new LinkedHashMap<>();
 PreviewCache(int bound){this.bound=bound;}
 V get(long revision,K key,Supplier<V> factory){if(this.revision!=revision){values.clear();this.revision=revision;}if(values.containsKey(key))return values.get(key);var value=factory.get();if(values.size()>=bound)values.remove(values.keySet().iterator().next());values.put(key,value);return value;}
 void clear(){values.clear();}
}
