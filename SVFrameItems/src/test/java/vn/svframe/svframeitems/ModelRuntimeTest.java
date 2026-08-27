package vn.svframe.svframeitems;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import vn.svframe.svframeitems.model.*;

import java.util.*;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class ModelRuntimeTest {
    @Test void deterministicStatRollAndLevelScaling(){
        StatRollSpec spec=new StatRollSpec("attack_damage",5,10,.5,2,NativeStatEngine.ModifierType.FLAT);
        ItemStat a=spec.roll(10,new SplittableRandom(42));ItemStat b=spec.roll(10,new SplittableRandom(42));
        assertEquals(a,b);assertTrue(a.value()>=9.5&&a.value()<=14.5);
    }
    @Test void upgradeChanceUsesExplicitLevelsThenDecay(){
        UpgradeTemplate template=new UpgradeTemplate("standard",10,1,.9,false,.05,Map.of(10,.3));
        assertEquals(1d,template.chanceForNextLevel(0),1e-9);assertEquals(.9d,template.chanceForNextLevel(1),1e-9);assertEquals(.3d,template.chanceForNextLevel(9),1e-9);assertEquals(0d,template.chanceForNextLevel(10));
    }
    @Test void socketColorMatchingIsStrictExceptAny(){
        SocketState red=new SocketState("red",null);assertTrue(red.accepts("red"));assertTrue(red.accepts("any"));assertFalse(red.accepts("blue"));
    }
    @Test void effectiveStatsScaleBaseAndEmbeddedGemIndependently(){
        ItemStat base=new ItemStat("ATTACK_DAMAGE",10,NativeStatEngine.ModifierType.FLAT);
        ItemStat gemStat=new ItemStat("ATTACK_DAMAGE",4,NativeStatEngine.ModifierType.FLAT);
        EmbeddedGem gem=new EmbeddedGem(UUID.randomUUID(),"ruby","gem","rare",1,2,1,4,0,"red",List.of(gemStat));
        ItemInstance item=new ItemInstance(UUID.randomUUID(),"blade","sword","rare",10,3,1,1,0,List.of(base),List.of(new SocketState("red",gem)));
        double value=item.effectiveStats(.10,ignored->.05).getFirst().value();
        assertEquals(10*1.3+4*1.1,value,1e-9);
    }
    @Test void metadataSurvivesAllStateTransitions(){
        ItemInstance item=new ItemInstance(UUID.randomUUID(),"blade","sword","rare",10,0,1,9,0,List.of(),List.of(),Map.of("integration:owner","alpha"));
        ItemInstance upgraded=item.withUpgradeLevel(1).withSockets(List.of(new SocketState("red",null))).withDefinitionRevision(2);
        assertEquals("alpha",upgraded.metadata().get("integration:owner"));assertTrue(upgraded.stateRevision()>item.stateRevision());
        assertEquals("beta",upgraded.withMetadata("integration:owner","beta").metadata().get("integration:owner"));
        assertFalse(upgraded.withMetadata("integration:owner",null).metadata().containsKey("integration:owner"));
    }
    @Test void lootLevelContextClampsRatherThanRandomizes(){
        LootTableDefinition.Entry entry=new LootTableDefinition.Entry("blade",1,1,1,1,5,20);
        assertEquals(5,entry.clampLevel(1));assertEquals(12,entry.clampLevel(12));assertEquals(20,entry.clampLevel(99));
    }
}
