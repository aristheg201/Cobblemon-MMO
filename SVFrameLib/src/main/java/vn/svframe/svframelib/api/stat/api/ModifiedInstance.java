package vn.svframe.svframelib.api.stat.api;

import vn.svframe.svframelib.player.modifier.ModifierType;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class ModifiedInstance<T extends InstanceModifier> {
    protected final Map<UUID,T> modifierMap=new LinkedHashMap<>();
    public double getTotal(double base){return getFilteredTotal(base,m->true,Function.identity());}
    public double getFilteredTotal(double base,Predicate<T> filter){return getFilteredTotal(base,filter,Function.identity());}
    public double getTotal(double base,Function<T,T> editor){return getFilteredTotal(base,m->true,editor);}
    public double getFilteredTotal(double base,Predicate<T> filter,Function<T,T> editor){double flat=base,add=1d,relative=1d;for(T original:getModifiers()){if(!filter.test(original))continue;T mod=editor.apply(original);if(mod==null)continue;switch(mod.getType()){case FLAT->flat+=mod.getValue();case ADDITIVE_MULTIPLIER->add+=mod.getValue()/100d;case RELATIVE->relative*=1d+mod.getValue()/100d;}}return flat*add*relative;}
    public T getModifier(String key){for(T m:getModifiers())if(Objects.equals(m.getKey(),key))return m;return null;}public T getModifier(UUID id){return modifierMap.get(id);}
    public void addModifier(T modifier){registerModifier(modifier);}public void registerModifier(T modifier){modifierMap.put(modifier.getUniqueId(),modifier);}public void removeModifier(UUID id){modifierMap.remove(id);}public void remove(String key){modifierMap.values().removeIf(m->Objects.equals(m.getKey(),key));}public boolean isEmpty(){return modifierMap.isEmpty();}public void removeIf(Predicate<String> predicate){modifierMap.values().removeIf(m->predicate.test(m.getKey()));}public Collection<T> getModifiers(){return List.copyOf(modifierMap.values());}public Set<UUID> getIds(){return Set.copyOf(modifierMap.keySet());}public Set<String> getKeys(){Set<String>s=new LinkedHashSet<>();for(T m:getModifiers())s.add(m.getKey());return s;}public boolean contains(String key){return getModifier(key)!=null;}
}
