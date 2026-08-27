package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.runtime.AbilityRuntime;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AbilityRuntimeTest {
    @Test void resolvesSvFrameLibInvocationPayloadWithoutOwningCooldownState(){
        ItemAbility equip=new ItemAbility(ItemAbility.Trigger.EQUIP,"integration_skill",1,0,Map.of("power",2d));
        ItemDefinition definition=new ItemDefinition("blade","sword","minecraft:iron_sword","Blade",1,1,1,100,Map.of("common",1),List.of(),List.of(),null,null,List.of(equip),null);
        ItemInstance instance=new ItemInstance(UUID.randomUUID(),"blade","sword","common",25,3,1,1234,0,List.of(),List.of(),Map.of("integration:mode","test"));
        var first=AbilityRuntime.resolve(instance,definition,ItemAbility.Trigger.EQUIP,new SplittableRandom(1));
        var second=AbilityRuntime.resolve(instance,definition,ItemAbility.Trigger.EQUIP,new SplittableRandom(1));
        assertEquals(first,second);assertEquals(1,first.size());assertEquals("integration_skill",first.getFirst().skill());
        assertEquals(25,first.getFirst().parameters().get("item_level"));assertEquals(3,first.getFirst().parameters().get("upgrade_level"));assertEquals(instance.instanceId().toString(),first.getFirst().parameters().get("item_instance_id"));assertEquals(instance.metadata(),first.getFirst().parameters().get("item_metadata"));
    }
    @Test void triggerFilteringAndChanceAreDeterministicWithInjectedRandom(){
        ItemAbility attack=new ItemAbility(ItemAbility.Trigger.ATTACK,"hit",.5,0,Map.of());
        ItemDefinition definition=new ItemDefinition("blade","sword","minecraft:iron_sword","Blade",1,1,1,1,Map.of("common",1),List.of(),List.of(),null,null,List.of(attack),null);
        ItemInstance instance=new ItemInstance(UUID.randomUUID(),"blade","sword","common",1,0,1,1,0,List.of(),List.of());
        assertTrue(AbilityRuntime.resolve(instance,definition,ItemAbility.Trigger.USE,new SplittableRandom(1)).isEmpty());
        assertEquals(AbilityRuntime.resolve(instance,definition,ItemAbility.Trigger.ATTACK,new SplittableRandom(99)),AbilityRuntime.resolve(instance,definition,ItemAbility.Trigger.ATTACK,new SplittableRandom(99)));
        ItemAbility never=new ItemAbility(ItemAbility.Trigger.ATTACK,"never",0,0,Map.of());
        ItemDefinition zeroChance=new ItemDefinition("zero","sword","minecraft:iron_sword","Zero",1,1,1,1,Map.of("common",1),List.of(),List.of(),null,null,List.of(never),null);
        assertTrue(AbilityRuntime.resolve(instance,zeroChance,ItemAbility.Trigger.ATTACK,new SplittableRandom(0)).isEmpty());
    }
}
