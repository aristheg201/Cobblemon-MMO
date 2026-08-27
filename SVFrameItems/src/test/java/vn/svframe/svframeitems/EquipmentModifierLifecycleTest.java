package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import vn.svframe.svframeitems.runtime.EquipmentModifierService;
import vn.svframe.svframeitems.runtime.EquipmentTransitionPlanner;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentModifierLifecycleTest {
    @Test void replacementIsAtomicDeduplicatedAndRemovesSetBonuses(){
        SVFrameItemsRegistry registry=new SVFrameItemsRegistry();
        registry.registerExternal(new ItemType("sword",NativeStatEngine.ModifierSource.MELEE_WEAPON,Set.of(NativeStatEngine.EquipmentSlot.MAIN_HAND),1));
        registry.registerExternal(new ItemType("armor",NativeStatEngine.ModifierSource.ARMOR,Set.of(NativeStatEngine.EquipmentSlot.HEAD),1));
        registry.registerExternal(new ItemRarity("common","Common",1,0));
        ItemDefinition bladeDef=new ItemDefinition("blade","sword","minecraft:iron_sword","Blade",1,1,1,1,Map.of("common",1),List.of(),List.of(),"duo",null,List.of(),null);
        ItemDefinition helmDef=new ItemDefinition("helm","armor","minecraft:iron_helmet","Helm",1,1,1,1,Map.of("common",1),List.of(),List.of(),"duo",null,List.of(),null);
        registry.registerExternal(bladeDef);registry.registerExternal(helmDef);
        NavigableMap<Integer,List<ItemStat>> bonuses=new TreeMap<>();bonuses.put(2,List.of(stat("ATTACK_DAMAGE",3),stat("MAX_HEALTH",10)));
        registry.registerExternal(new ItemSetDefinition("duo","Duo",Set.of("blade","helm"),bonuses));registry.validateSnapshot();
        UpgradeService upgrades=new UpgradeService(registry,new ItemGenerator(registry,new ItemFormatter()));NativeStatEngine engine=new NativeStatEngine();EquipmentModifierService service=new EquipmentModifierService(registry,upgrades,engine);UUID player=UUID.randomUUID();
        ItemInstance blade=item("blade","sword",stat("ATTACK_DAMAGE",5));ItemInstance helm=item("helm","armor",stat("MAX_HEALTH",2));
        service.replace(player,List.of(new EquipmentModifierService.Equipped(blade,NativeStatEngine.EquipmentSlot.MAIN_HAND),new EquipmentModifierService.Equipped(blade,NativeStatEngine.EquipmentSlot.MAIN_HAND),new EquipmentModifierService.Equipped(helm,NativeStatEngine.EquipmentSlot.HEAD)));
        assertEquals(8d,engine.finalValue(player,"ATTACK_DAMAGE",NativeStatEngine.EquipmentSlot.MAIN_HAND),1e-9);assertEquals(12d,engine.finalValue(player,"MAX_HEALTH",NativeStatEngine.EquipmentSlot.MAIN_HAND),1e-9);assertEquals(4,service.activeModifierCount(player));
        service.replace(player,List.of(new EquipmentModifierService.Equipped(blade,NativeStatEngine.EquipmentSlot.MAIN_HAND)));
        assertEquals(5d,engine.finalValue(player,"ATTACK_DAMAGE",NativeStatEngine.EquipmentSlot.MAIN_HAND),1e-9);assertEquals(0d,engine.finalValue(player,"MAX_HEALTH",NativeStatEngine.EquipmentSlot.MAIN_HAND),1e-9);assertEquals(1,service.activeModifierCount(player));
        service.clear(player);assertEquals(0d,engine.finalValue(player,"ATTACK_DAMAGE",NativeStatEngine.EquipmentSlot.MAIN_HAND),1e-9);
    }

    @Test void lifecyclePlannerTreatsSlotMovesAsUnequipThenEquipButStateRefreshAsStable(){
        UUID id=UUID.randomUUID();
        var main=new EquipmentTransitionPlanner.Location("vanilla:main_hand",NativeStatEngine.EquipmentSlot.MAIN_HAND);
        var off=new EquipmentTransitionPlanner.Location("vanilla:off_hand",NativeStatEngine.EquipmentSlot.OFF_HAND);
        var stable=EquipmentTransitionPlanner.plan(Map.of(id,main),Map.of(id,main));
        assertTrue(stable.unequip().isEmpty());assertTrue(stable.equip().isEmpty());
        var moved=EquipmentTransitionPlanner.plan(Map.of(id,main),Map.of(id,off));
        assertEquals(Set.of(id),moved.unequip());assertEquals(Set.of(id),moved.equip());
        var removed=EquipmentTransitionPlanner.plan(Map.of(id,main),Map.of());
        assertEquals(Set.of(id),removed.unequip());assertTrue(removed.equip().isEmpty());
    }
    private static ItemStat stat(String id,double value){return new ItemStat(id,value,NativeStatEngine.ModifierType.FLAT);}
    private static ItemInstance item(String id,String type,ItemStat stat){return new ItemInstance(UUID.randomUUID(),id,type,"common",1,0,1,1,0,List.of(stat),List.of());}
}
