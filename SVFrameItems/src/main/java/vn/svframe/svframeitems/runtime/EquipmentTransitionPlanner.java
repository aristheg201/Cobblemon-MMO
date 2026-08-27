package vn.svframe.svframeitems.runtime;

import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;

import java.util.*;

/** Pure lifecycle transition planner: item identity plus logical equipment location. */
public final class EquipmentTransitionPlanner {
    public record Location(String key, NativeStatEngine.EquipmentSlot slot) {
        public Location { key=Objects.requireNonNull(key,"key"); slot=Objects.requireNonNull(slot,"slot"); }
    }
    public record Transitions(Set<UUID> unequip, Set<UUID> equip) {
        public Transitions { unequip=Set.copyOf(unequip); equip=Set.copyOf(equip); }
    }
    private EquipmentTransitionPlanner() {}
    public static Transitions plan(Map<UUID,Location> previous,Map<UUID,Location> current){
        Objects.requireNonNull(previous,"previous");Objects.requireNonNull(current,"current");
        LinkedHashSet<UUID> unequip=new LinkedHashSet<>(),equip=new LinkedHashSet<>();
        for(var entry:previous.entrySet()){Location now=current.get(entry.getKey());if(now==null||!entry.getValue().equals(now))unequip.add(entry.getKey());}
        for(var entry:current.entrySet()){Location old=previous.get(entry.getKey());if(old==null||!entry.getValue().equals(old))equip.add(entry.getKey());}
        return new Transitions(unequip,equip);
    }
}
