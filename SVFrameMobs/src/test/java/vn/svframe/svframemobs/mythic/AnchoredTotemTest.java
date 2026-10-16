package vn.svframe.svframemobs.mythic;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.svframe.svframemobs.mythic.MythicEngine.*;

class AnchoredTotemTest {
    @Test void sourceBlackholeUsesIntervalRoundedExpiryAndRetainsItsOriginalPoint() {
        var world=new MythicEngineTest.World();var engine=new MythicEngine(world);
        Point anchor=new Point("minecraft:overworld",6,66.1,0);
        Context context=new Context(MythicEngineTest.ACTOR,null,new Target(null,anchor),anchor,Map.of(),new HashMap<>(),new Execution(),0);
        List<String> observed=new ArrayList<>();
        AnchoredTotem.start(engine,context,10,4,()->true,()->observed.add("start:0"),
            ()->observed.add("tick:"+engine.currentTick()+":"+context.origin().x()),
            ()->observed.add("end:"+engine.currentTick()+":"+context.origin().x()));
        world.selected=List.of(new Target(null,anchor.add(30,0,0)));
        for(int i=0;i<13;i++)engine.tick();
        assertEquals(List.of("start:0","tick:1:6.0","tick:5:6.0","tick:9:6.0","end:13:6.0"),observed);
        assertEquals(0,engine.scheduledCount());
    }
    @Test void reloadCancelsAnOldTotemWithoutExecutingAnExplosionFromItsOldDefinitions() {
        var world=new MythicEngineTest.World();var engine=new MythicEngine(world);
        var p=MythicEngineTest.ORIGIN;
        var context=new Context(MythicEngineTest.ACTOR,null,new Target(null,p),p,Map.of(),new HashMap<>(),new Execution(),0);
        List<String> observed=new ArrayList<>();
        AnchoredTotem.start(engine,context,10,4,()->true,()->{},()->observed.add("tick"),()->observed.add("end"));
        engine.tick();engine.replace(Map.of(),Map.of());for(int i=0;i<20;i++)engine.tick();
        assertEquals(List.of("tick"),observed);
    }
}
