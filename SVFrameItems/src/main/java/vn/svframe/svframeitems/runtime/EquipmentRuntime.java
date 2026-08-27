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
    private final SVFrameItemsRegistry registry; private final UpgradeService upgrades; private final AbilityRuntime abilities; private final NativeStatEngine engine=SVFrameLibStatMod.engine();
    private final Map<UUID,PlayerState> states=new ConcurrentHashMap<>();
    public EquipmentRuntime(SVFrameItemsRegistry registry,UpgradeService upgrades,AbilityRuntime abilities){this.registry=Objects.requireNonNull(registry);this.upgrades=Objects.requireNonNull(upgrades);this.abilities=Objects.requireNonNull(abilities);}
    public void tick(MinecraftServer server){for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())refresh(player);}
    public void clear(UUID player){PlayerState old=states.remove(player);if(old!=null)remove(player,old);abilities.clear(player);}
    public void clear(){for(UUID id:List.copyOf(states.keySet()))clear(id);}
    public void refresh(ServerPlayerEntity player){List<EquipmentProvider.EquippedItem> equipped=collect(player);String fingerprint=fingerprint(equipped);PlayerState previous=states.get(player.getUuid());if(previous!=null&&previous.fingerprint.equals(fingerprint))return;if(previous!=null)remove(player.getUuid(),previous);
        List<ModifierRef> refs=new ArrayList<>();Map<String,Integer> setCounts=new LinkedHashMap<>();Set<UUID> previousItems=previous==null?Set.of():previous.itemIds;
        Set<UUID> itemIds=new LinkedHashSet<>();
        engine.bufferUpdates(player.getUuid(),()->{
            for(EquipmentProvider.EquippedItem equippedItem:equipped){Optional<ItemInstance> decoded=ItemCodec.read(equippedItem.stack());if(decoded.isEmpty())continue;ItemInstance instance=decoded.get();ItemDefinition definition=registry.item(instance.definitionId());ItemType type=definition==null?null:registry.type(definition.typeId());if(definition==null||type==null||!type.canEquip(equippedItem.slot()))continue;itemIds.add(instance.instanceId());if(definition.setId()!=null)setCounts.merge(definition.setId(),1,Integer::sum);
                double targetMultiplier=upgrades.statMultiplier(instance);for(ItemStat stat:instance.effectiveStats(targetMultiplier,upgrades::statMultiplier)){UUID modifier=engine.register(player.getUuid(),stat.stat(),"svframeitems:"+instance.instanceId(),stat.value(),stat.type(),equippedItem.slot(),type.modifierSource());refs.add(new ModifierRef(stat.stat(),modifier));}
                if(!previousItems.contains(instance.instanceId()))abilities.triggerEquip(player,equippedItem.stack());
            }
            for(Map.Entry<String,Integer> setEntry:setCounts.entrySet()){ItemSetDefinition set=registry.set(setEntry.getKey());if(set==null)continue;for(ItemStat stat:set.activeBonuses(setEntry.getValue())){UUID modifier=engine.register(player.getUuid(),stat.stat(),"svframeitems:set:"+set.id(),stat.value(),stat.type(),NativeStatEngine.EquipmentSlot.OTHER,NativeStatEngine.ModifierSource.OTHER);refs.add(new ModifierRef(stat.stat(),modifier));}}
        });
        states.put(player.getUuid(),new PlayerState(fingerprint,List.copyOf(refs),Set.copyOf(itemIds)));
    }
    private void remove(UUID player,PlayerState state){engine.bufferUpdates(player,()->{for(ModifierRef ref:state.modifiers)engine.remove(player,ref.stat,ref.id);});}
    private static List<EquipmentProvider.EquippedItem> collect(ServerPlayerEntity player){List<EquipmentProvider.EquippedItem> out=new ArrayList<>();
        out.add(new EquipmentProvider.EquippedItem("vanilla:head",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD),NativeStatEngine.EquipmentSlot.HEAD));
        out.add(new EquipmentProvider.EquippedItem("vanilla:chest",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST),NativeStatEngine.EquipmentSlot.CHEST));
        out.add(new EquipmentProvider.EquippedItem("vanilla:legs",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS),NativeStatEngine.EquipmentSlot.LEGS));
        out.add(new EquipmentProvider.EquippedItem("vanilla:feet",player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET),NativeStatEngine.EquipmentSlot.FEET));
        out.add(new EquipmentProvider.EquippedItem("vanilla:main_hand",player.getMainHandStack(),NativeStatEngine.EquipmentSlot.MAIN_HAND));
        out.add(new EquipmentProvider.EquippedItem("vanilla:off_hand",player.getOffHandStack(),NativeStatEngine.EquipmentSlot.OFF_HAND));
        for(EquipmentProvider provider:EquipmentProviderRegistry.providers()){Collection<EquipmentProvider.EquippedItem> extra=provider.equipment(player);if(extra!=null)out.addAll(extra);}return out;}
    private static String fingerprint(List<EquipmentProvider.EquippedItem> items){StringBuilder out=new StringBuilder();for(EquipmentProvider.EquippedItem item:items){out.append(item.key()).append('=');ItemCodec.read(item.stack()).ifPresent(value->out.append(value.instanceId()).append(':').append(value.stateRevision()));out.append(';');}return out.toString();}
    private record ModifierRef(String stat,UUID id){}
    private record PlayerState(String fingerprint,List<ModifierRef> modifiers,Set<UUID> itemIds){}
}
