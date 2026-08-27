package vn.svframe.svframeitems.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.SVFrameLibStatMod;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class EquipmentRuntime {
    private final SVFrameItemsRegistry registry; private final UpgradeService upgrades; private final AbilityRuntime abilities; private final EquipmentModifierService modifiers;
    private final Map<UUID,PlayerState> states=new ConcurrentHashMap<>();
    public EquipmentRuntime(SVFrameItemsRegistry registry,UpgradeService upgrades,AbilityRuntime abilities){this.registry=Objects.requireNonNull(registry);this.upgrades=Objects.requireNonNull(upgrades);this.abilities=Objects.requireNonNull(abilities);this.modifiers=new EquipmentModifierService(registry,this.upgrades,SVFrameLibStatMod.engine());}
    public record EquippedView(String locationKey, NativeStatEngine.EquipmentSlot slot, ItemInstance instance, ItemDefinition definition, ItemType type, List<ItemStat> effectiveStats) {
        public EquippedView { locationKey=Objects.requireNonNull(locationKey);slot=Objects.requireNonNull(slot);instance=Objects.requireNonNull(instance);definition=Objects.requireNonNull(definition);type=Objects.requireNonNull(type);effectiveStats=List.copyOf(effectiveStats); }
    }
    public record Evaluation(List<EquippedView> items, Map<String,Integer> setPieceCounts, List<ItemStat> activeSetBonuses) {
        public Evaluation { items=List.copyOf(items);setPieceCounts=Map.copyOf(setPieceCounts);activeSetBonuses=List.copyOf(activeSetBonuses); }
    }

    public void tick(MinecraftServer server){for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())refresh(player);}

    /** Side-effect free view for integrations that need the exact equipment state SVFrameItems would apply. */
    public Evaluation evaluate(ServerPlayerEntity player){
        Objects.requireNonNull(player,"player");
        LinkedHashMap<UUID,EquippedView> unique=new LinkedHashMap<>();
        Map<String,Set<String>> pieces=new LinkedHashMap<>();
        for(EquipmentProvider.EquippedItem equippedItem:collect(player)){
            Optional<ItemInstance> decoded=ItemCodec.read(equippedItem.stack());if(decoded.isEmpty())continue;
            ItemInstance instance=decoded.get();ItemDefinition definition=registry.item(instance.definitionId());if(definition==null)continue;
            ItemType type=registry.type(definition.typeId());if(type==null||!type.canEquip(equippedItem.slot()))continue;
            List<ItemStat> effective=instance.effectiveStats(upgrades.statMultiplier(instance),upgrades::statMultiplier);
            EquippedView view=new EquippedView(equippedItem.key(),equippedItem.slot(),instance,definition,type,effective);
            if(unique.putIfAbsent(instance.instanceId(),view)!=null)continue;
            if(definition.setId()!=null){ItemSetDefinition set=registry.set(definition.setId());if(set!=null&&set.pieces().contains(definition.id()))pieces.computeIfAbsent(set.id(),ignored->new LinkedHashSet<>()).add(definition.id());}
        }
        LinkedHashMap<String,Integer> counts=new LinkedHashMap<>();List<ItemStat> bonuses=new ArrayList<>();
        for(Map.Entry<String,Set<String>> entry:pieces.entrySet()){counts.put(entry.getKey(),entry.getValue().size());ItemSetDefinition set=registry.set(entry.getKey());if(set!=null)bonuses.addAll(set.activeBonuses(entry.getValue().size()));}
        return new Evaluation(List.copyOf(unique.values()),counts,bonuses);
    }
    /** Disconnect/server-stop cleanup is not a gameplay unequip transition. */
    public void clear(ServerPlayerEntity player){Objects.requireNonNull(player);clear(player.getUuid());}
    public void clear(UUID player){states.remove(player);modifiers.clear(player);}
    public void clear(){for(UUID id:List.copyOf(states.keySet()))clear(id);}
    public void refresh(ServerPlayerEntity player){refresh(player,false);}
    /** Force re-applies the stat snapshot while still only firing ability transitions for real location changes. */
    public void refresh(ServerPlayerEntity player,boolean force){
        Objects.requireNonNull(player,"player");
        List<EquipmentProvider.EquippedItem> collected=collect(player);String fingerprint=fingerprint(collected,registry.revision());PlayerState previous=states.get(player.getUuid());if(!force&&previous!=null&&previous.fingerprint.equals(fingerprint))return;
        LinkedHashMap<UUID,ItemStackRef> currentItems=new LinkedHashMap<>();List<EquipmentModifierService.Equipped> equipped=new ArrayList<>();
        for(EquipmentProvider.EquippedItem equippedItem:collected){Optional<ItemInstance> decoded=ItemCodec.read(equippedItem.stack());if(decoded.isEmpty())continue;ItemInstance instance=decoded.get();ItemDefinition definition=registry.item(instance.definitionId());if(definition==null)continue;ItemType type=registry.type(definition.typeId());if(type==null||!type.canEquip(equippedItem.slot()))continue;ItemStackRef ref=new ItemStackRef(equippedItem.stack().copy(),equippedItem.key(),equippedItem.slot());if(currentItems.putIfAbsent(instance.instanceId(),ref)!=null)continue;equipped.add(new EquipmentModifierService.Equipped(instance,equippedItem.slot()));}
        Map<UUID,ItemStackRef> previousItems=previous==null?Map.of():previous.items;
        Map<UUID,EquipmentTransitionPlanner.Location> previousLocations=locations(previousItems),currentLocations=locations(currentItems);
        EquipmentTransitionPlanner.Transitions transitions=EquipmentTransitionPlanner.plan(previousLocations,currentLocations);
        for(UUID id:transitions.unequip())abilities.triggerUnequip(player,previousItems.get(id).stack);
        modifiers.replace(player.getUuid(),equipped);
        for(UUID id:transitions.equip())abilities.triggerEquip(player,currentItems.get(id).stack);
        states.put(player.getUuid(),new PlayerState(fingerprint,Map.copyOf(currentItems)));
    }
    private static List<EquipmentProvider.EquippedItem> collect(ServerPlayerEntity player){List<EquipmentProvider.EquippedItem> out=new ArrayList<>();
        out.add(new EquipmentProvider.EquippedItem("vanilla:head",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),NativeStatEngine.EquipmentSlot.HEAD));
        out.add(new EquipmentProvider.EquippedItem("vanilla:chest",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),NativeStatEngine.EquipmentSlot.CHEST));
        out.add(new EquipmentProvider.EquippedItem("vanilla:legs",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),NativeStatEngine.EquipmentSlot.LEGS));
        out.add(new EquipmentProvider.EquippedItem("vanilla:feet",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),NativeStatEngine.EquipmentSlot.FEET));
        out.add(new EquipmentProvider.EquippedItem("vanilla:main_hand",player.getMainHandStack(),NativeStatEngine.EquipmentSlot.MAIN_HAND));
        out.add(new EquipmentProvider.EquippedItem("vanilla:off_hand",player.getOffHandStack(),NativeStatEngine.EquipmentSlot.OFF_HAND));
        for(EquipmentProvider provider:EquipmentProviderRegistry.providers()){Collection<EquipmentProvider.EquippedItem> extra=provider.equipment(player);if(extra!=null)for(EquipmentProvider.EquippedItem item:extra)if(item!=null)out.add(item);}return out;}
    private static String fingerprint(List<EquipmentProvider.EquippedItem> items,long registryRevision){StringBuilder out=new StringBuilder().append("registry:").append(registryRevision).append(';');for(EquipmentProvider.EquippedItem item:items){out.append(item.key()).append('@').append(item.slot()).append('=');ItemCodec.read(item.stack()).ifPresent(value->out.append(value.instanceId()).append(':').append(value.stateRevision()));out.append(';');}return out.toString();}
    private static Map<UUID,EquipmentTransitionPlanner.Location> locations(Map<UUID,ItemStackRef> items){LinkedHashMap<UUID,EquipmentTransitionPlanner.Location> out=new LinkedHashMap<>();items.forEach((id,ref)->out.put(id,new EquipmentTransitionPlanner.Location(ref.locationKey,ref.slot)));return Map.copyOf(out);}
    private record ItemStackRef(net.minecraft.item.ItemStack stack,String locationKey,NativeStatEngine.EquipmentSlot slot){}
    private record PlayerState(String fingerprint,Map<UUID,ItemStackRef> items){}
}
