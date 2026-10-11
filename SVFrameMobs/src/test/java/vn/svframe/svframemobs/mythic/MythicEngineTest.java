package vn.svframe.svframemobs.mythic;

import org.junit.jupiter.api.Test;
import vn.svframe.svframelib.fabric.runtime.script.ExpressionRuntime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.svframe.svframemobs.mythic.MythicEngine.*;

class MythicEngineTest {
    static final UUID ACTOR=UUID.randomUUID(),TARGET=UUID.randomUUID();
    static final Point ORIGIN=new Point("minecraft:overworld",5,64,8);
    static final class World implements Platform {
        final List<String> effects=new ArrayList<>(),failures=new ArrayList<>();boolean alive=true,condition=true;int predicates;
        List<Target> selected=List.of(new Target(TARGET,null));
        @Override public boolean valid(Context c){return alive;}
        @Override public boolean condition(MythicLine.Condition condition,Context c,Target t){predicates++;return this.condition;}
        @Override public List<Target> select(MythicLine.Selector selector,Context c){return selected;}
        @Override public double number(String expression,Context c,Target t){return new ExpressionRuntime().evaluate(text(expression,c,t),Map.of());}
        @Override public String text(String expression,Context c,Target t){for(var p:c.parameters().entrySet())expression=expression.replace("<modifier."+p.getKey()+">",p.getValue());return expression;}
        @Override public void execute(MythicLine action,Context c,Target t,MythicEngine engine){effects.add(action.mechanic()+":"+(action.mechanic().equals("damage")?Double.toString(number(action.value("a","0"),c,t)):action.value("m",""))+":"+t.entity()+":"+c.origin().x());}
        @Override public List<String> validate(MythicDefinition d){return d.skills().stream().filter(a->a.mechanic().equals("unknown")).map(a->"Unsupported mechanic unknown").toList();}
        @Override public void failed(String id,Exception failure){failures.add(failure.getMessage());}
    }
    static MythicDefinition definition(String id,String...lines){return MythicDefinition.parse(id,"fixture.yml",Map.of("Skills",List.of(lines)));}
    static boolean cast(MythicEngine engine,String id){return engine.cast(id,ACTOR,TARGET,new Target(TARGET,null),ORIGIN,Map.of("damage",7));}

    @Test void parsesActualInlineConditionsAndNumericExpressionsWithoutSplittingParameters(){
        var line=MythicLine.parse("damage{a=\"<modifier.damage> * 1.5\";pkb=true} @EIR{r=5;limit=40;sort=NEAREST;conditions=[- isLiving true - iscaster false - entitytype{t=ARMOR_STAND} false]}");
        assertEquals("<modifier.damage> * 1.5",line.value("a",""));assertEquals(3,line.selector().conditions().size());assertEquals("ARMOR_STAND",line.selector().conditions().get(2).parameters().get("t"));assertFalse(line.selector().conditions().get(1).expected());
    }
    @Test void commasRemainRandomSkillListMembersAndQuotedSemicolonsRemainText(){
        assertEquals("AttackOne 1,AttackTwo 3",MythicLine.parse("randomskill{skills=AttackOne 1,AttackTwo 3}").value("skills",""));
        assertEquals("first; second",MythicLine.parse("message{m=\"first; second\"}").value("m",""));
        assertThrows(IllegalArgumentException.class,()->MythicLine.parse("damage{a=4;a=6}"));
        assertThrows(IllegalArgumentException.class,()->MythicLine.parse("damage{a=4} @EIR{conditions=[- isLiving true}"));
    }
    @Test void delayedNestedGraphPreservesVictimAndOriginAndPassesModifierArguments(){
        World world=new World();MythicEngine engine=new MythicEngine(world);
        engine.replace(Map.of("root",definition("root","delay 3","skill{s=impact;damage=\"<modifier.damage> * 2\"}"),"impact",definition("impact","damage{a=\"<modifier.damage>\"}")),Map.of());
        assertTrue(cast(engine,"root"));assertTrue(world.effects.isEmpty());engine.tick();engine.tick();assertTrue(world.effects.isEmpty());engine.tick();
        assertEquals(List.of("damage:14.0:"+TARGET+":5.0"),world.effects);
    }
    @Test void repeatMeansInitialEffectPlusRequestedRepeats(){
        World world=new World();MythicEngine engine=new MythicEngine(world);engine.replace(Map.of("r",definition("r","message{m=impact;repeat=2;repeatInterval=2}")),Map.of());
        assertTrue(cast(engine,"r"));assertEquals(1,world.effects.size());for(int i=0;i<4;i++)engine.tick();assertEquals(3,world.effects.size());
    }
    @Test void selectedStormLocationBecomesTheNestedStrikeOrigin(){
        World world=new World();world.selected=List.of(new Target(null,new Point(ORIGIN.world(),12,64,18)));
        MythicEngine engine=new MythicEngine(world);engine.replace(Map.of("storm",definition("storm","skill{s=strike} @Forward{f=7}"),"strike",definition("strike","message{m=strike}")),Map.of());
        assertTrue(cast(engine,"storm"));assertEquals(List.of("message:strike:null:12.0"),world.effects);
    }
    @Test void movingSelectorsAreResolvedAtEachRepeatedExecution(){
        World world=new World();MythicEngine engine=new MythicEngine(world);engine.replace(Map.of("dash",definition("dash","message{m=step;repeat=2;repeatInterval=1} @Forward{f=10}")),Map.of());
        assertTrue(cast(engine,"dash"));UUID second=UUID.randomUUID(),third=UUID.randomUUID();
        world.selected=List.of(new Target(second,null));engine.tick();world.selected=List.of(new Target(third,null));engine.tick();
        assertEquals(3,world.effects.size());assertTrue(world.effects.get(1).contains(second.toString()));assertTrue(world.effects.get(2).contains(third.toString()));
    }
    @Test void unavailableDependencyPropagatesThroughEveryParentBeforeCasting(){
        World world=new World();MythicEngine engine=new MythicEngine(world);
        engine.replace(Map.of("root",definition("root","skill{s=child}"),"child",definition("child","skill{s=bad}"),"bad",definition("bad","unknown{}")),Map.of());
        assertFalse(engine.available("root"));assertFalse(cast(engine,"root"));assertTrue(world.effects.isEmpty());assertEquals("Unavailable meta-skill child",engine.failures().get("root").getFirst());
    }
    @Test void castInsteadSelectsExactlyOneBranchBeforeCostsAreCommitted(){
        World world=new World();MythicEngine engine=new MythicEngine(world);var root=MythicDefinition.parse("root","fixture",Map.of("Conditions",List.of("hasaura{auraName=test} castinstead alternate"),"Skills",List.of("message{m=wrong}")));
        engine.replace(Map.of("root",root,"alternate",definition("alternate","message{m=alternate}")),Map.of());
        var prepared=engine.prepare("root",ACTOR,TARGET,new Target(TARGET,null),ORIGIN,Map.of());assertNotNull(prepared);assertEquals(1,world.predicates);assertTrue(world.effects.isEmpty());
        assertTrue(engine.start(prepared));assertEquals(1,world.predicates);assertTrue(world.effects.getFirst().startsWith("message:alternate"));assertFalse(engine.start(prepared));
    }
    @Test void cooldownRejectsRepeatAndExpiresOnServerTicks(){
        World world=new World();MythicEngine engine=new MythicEngine(world);var skill=MythicDefinition.parse("r","fixture",Map.of("Cooldown",1,"Skills",List.of("message{m=hit}")));engine.replace(Map.of("r",skill),Map.of());
        assertTrue(cast(engine,"r"));assertFalse(cast(engine,"r"));for(int i=0;i<19;i++)engine.tick();assertFalse(cast(engine,"r"));engine.tick();assertTrue(cast(engine,"r"));
    }
    @Test void reloadAndActorRemovalInvalidateDelayedWorldMutations(){
        World world=new World();MythicEngine engine=new MythicEngine(world);var skill=definition("r","delay 2","message{m=stale}");engine.replace(Map.of("r",skill),Map.of());assertTrue(cast(engine,"r"));
        engine.replace(Map.of("r",skill),Map.of());engine.tick();engine.tick();assertTrue(world.effects.isEmpty());
        assertTrue(cast(engine,"r"));world.alive=false;engine.tick();engine.tick();assertTrue(world.effects.isEmpty());
    }
    @Test void supportedRecursiveGraphsCannotOverflowTheStackOrScheduler(){
        World world=new World();MythicEngine engine=new MythicEngine(world);engine.replace(Map.of("r",definition("r","skill{s=r}")),Map.of());
        assertTrue(cast(engine,"r"));assertEquals(0,engine.scheduledCount());assertTrue(world.effects.isEmpty());
    }
    @Test void metadataTyposAreReportedInsteadOfDroppingConditions(){
        assertThrows(IllegalArgumentException.class,()->MythicDefinition.parse("r","authored.yml",Map.of("Conditons",List.of("hasaura{a=test} false"),"Skills",List.of("damage{a=2}"))));
    }
}
