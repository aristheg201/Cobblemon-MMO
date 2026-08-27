package vn.svframe.svframelib.api.stat;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.stat.api.ModifiedInstance;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.fabric.SVFrameLibStatMod;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** Public stat instance backed one-to-one by the native Fabric stat engine. */
public final class StatInstance extends ModifiedInstance<StatModifier> {
    private final StatMap map;
    private final String stat;
    private final NativeStatEngine.StatInstance nativeInstance;

    StatInstance(StatMap map,String stat){this(map,stat,SVFrameLibStatMod.engine().instance(map.getData().getUniqueId(),stat));}
    StatInstance(StatMap map,String stat,NativeStatEngine.StatInstance nativeInstance){this.map=Objects.requireNonNull(map);this.stat=Objects.requireNonNull(stat);this.nativeInstance=Objects.requireNonNull(nativeInstance);}
    public StatMap getMap(){return map;}public String getStat(){return stat;}
    public double getBase(){return nativeInstance.base();}public double getDefaultBase(){return nativeInstance.defaultBase();}
    public double getFinal(){return getFinal(EquipmentSlot.MAIN_HAND);}public double getFinal(EquipmentSlot slot){return nativeInstance.finalValue(NativeStatEngine.EquipmentSlot.valueOf(slot.name()));}
    public String formatFinal(){return nativeInstance.formatFinal();}public String format(double value){return nativeInstance.format(value);}
    @Override public StatModifier getModifier(UUID id){NativeStatEngine.Modifier n=nativeInstance.modifier(id);return n==null?null:StatModifier.fromNative(stat,n);}
    @Override public StatModifier getModifier(String key){for(StatModifier m:getModifiers())if(Objects.equals(m.getKey(),key))return m;return null;}
    @Override public Collection<StatModifier> getModifiers(){List<StatModifier> out=new ArrayList<>();for(NativeStatEngine.Modifier m:nativeInstance.modifiers())out.add(StatModifier.fromNative(stat,m));return List.copyOf(out);}
    @Override public Set<UUID> getIds(){return Set.copyOf(nativeInstance.modifierIds());}
    @Override public Set<String> getKeys(){Set<String>s=new LinkedHashSet<>();for(StatModifier m:getModifiers())s.add(m.getKey());return s;}
    public double getTotal(){return nativeInstance.total();}public double getTotal(EquipmentSlot slot){return nativeInstance.total(NativeStatEngine.EquipmentSlot.valueOf(slot.name()));}
    public double getTotal(double base){return nativeInstance.total(base,NativeStatEngine.EquipmentSlot.MAIN_HAND);}public double getTotal(double base,EquipmentSlot slot){return nativeInstance.total(base,NativeStatEngine.EquipmentSlot.valueOf(slot.name()));}
    @Override public void registerModifier(StatModifier modifier){nativeInstance.register(modifier.toNative());}
    @Override public void addModifier(StatModifier modifier){registerModifier(modifier);}
    @Override public void removeModifier(UUID id){nativeInstance.remove(id);}
    @Override public void remove(String key){nativeInstance.removeIf(m->Objects.equals(m.key(),key));}
    @Override public void removeIf(Predicate<String> predicate){nativeInstance.removeIf(m->predicate.test(m.key()));}
    @Override public boolean isEmpty(){return nativeInstance.isEmpty();}@Override public boolean contains(String key){return getModifier(key)!=null;}
    public void invalidateReferences(){}
    public void update(){if(!map.isBufferingUpdates()&&SVFrameLib.plugin!=null)SVFrameLib.plugin.getStats().runUpdate(this);}
    public void releaseUpdates(){if(SVFrameLib.plugin!=null)SVFrameLib.plugin.getStats().runUpdate(this);}
    public double getFilteredTotal(double base,Predicate<StatModifier> filter,Function<StatModifier,StatModifier> editor){double flat=base,add=1d,rel=1d;for(StatModifier original:getModifiers()){if(!filter.test(original))continue;StatModifier m=editor.apply(original);if(m==null)continue;switch(m.getType()){case FLAT->flat+=m.getValue();case ADDITIVE_MULTIPLIER->add+=m.getValue()/100d;case RELATIVE->rel*=1d+m.getValue()/100d;}}return flat*add*rel;}
}
