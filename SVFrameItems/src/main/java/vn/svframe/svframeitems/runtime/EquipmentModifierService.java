package vn.svframe.svframeitems.runtime;

import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframeitems.item.UpgradeService;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Applies one authoritative SVFrameItems modifier snapshot per player. */
public final class EquipmentModifierService {
    public record Equipped(ItemInstance instance, NativeStatEngine.EquipmentSlot slot) {
        public Equipped { Objects.requireNonNull(instance); Objects.requireNonNull(slot); }
    }
    private record ModifierRef(String stat, UUID id) {}
    private final SVFrameItemsRegistry registry; private final UpgradeService upgrades; private final NativeStatEngine engine;
    private final Map<UUID,List<ModifierRef>> active=new ConcurrentHashMap<>();

    public EquipmentModifierService(SVFrameItemsRegistry registry,UpgradeService upgrades,NativeStatEngine engine){this.registry=Objects.requireNonNull(registry);this.upgrades=Objects.requireNonNull(upgrades);this.engine=Objects.requireNonNull(engine);}

    public void replace(UUID playerId,Collection<Equipped> equipped){
        Objects.requireNonNull(playerId);Objects.requireNonNull(equipped);
        LinkedHashMap<UUID,Equipped> unique=new LinkedHashMap<>();
        for(Equipped value:equipped)unique.putIfAbsent(value.instance().instanceId(),value);
        List<ModifierRef> previous=active.getOrDefault(playerId,List.of()); List<ModifierRef> next=new ArrayList<>();
        Map<String,Set<String>> setPieces=new LinkedHashMap<>();
        engine.bufferUpdates(playerId,()->{
            for(ModifierRef ref:previous)engine.remove(playerId,ref.stat(),ref.id());
            for(Equipped value:unique.values()){
                ItemInstance instance=value.instance();ItemDefinition definition=registry.item(instance.definitionId());if(definition==null)continue;
                ItemType type=registry.type(definition.typeId());if(type==null||!type.canEquip(value.slot()))continue;
                if(definition.setId()!=null){ItemSetDefinition set=registry.set(definition.setId());if(set!=null&&set.pieces().contains(definition.id()))setPieces.computeIfAbsent(set.id(),ignored->new LinkedHashSet<>()).add(definition.id());}
                for(ItemStat stat:instance.effectiveStats(upgrades.statMultiplier(instance),upgrades::statMultiplier)){
                    UUID modifier=engine.register(playerId,stat.stat(),"svframeitems:"+instance.instanceId(),stat.value(),stat.type(),value.slot(),type.modifierSource());
                    next.add(new ModifierRef(stat.stat(),modifier));
                }
            }
            for(Map.Entry<String,Set<String>> entry:setPieces.entrySet()){
                ItemSetDefinition set=registry.set(entry.getKey());if(set==null)continue;
                for(ItemStat stat:set.activeBonuses(entry.getValue().size())){
                    UUID modifier=engine.register(playerId,stat.stat(),"svframeitems:set:"+set.id(),stat.value(),stat.type(),NativeStatEngine.EquipmentSlot.OTHER,NativeStatEngine.ModifierSource.OTHER);
                    next.add(new ModifierRef(stat.stat(),modifier));
                }
            }
        });
        active.put(playerId,List.copyOf(next));
    }

    public void clear(UUID playerId){List<ModifierRef> previous=active.remove(playerId);if(previous==null)return;engine.bufferUpdates(playerId,()->{for(ModifierRef ref:previous)engine.remove(playerId,ref.stat(),ref.id());});}
    public int activeModifierCount(UUID playerId){return active.getOrDefault(playerId,List.of()).size();}
}
