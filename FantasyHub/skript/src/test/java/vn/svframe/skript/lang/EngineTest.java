package vn.svframe.skript.lang;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import vn.svframe.skript.lang.Platform.*;

class EngineTest {
    static final class TestPlatform implements Platform {
        final List<String> messages=new ArrayList<>(),commands=new ArrayList<>(),errors=new ArrayList<>(),potions=new ArrayList<>();
        final Map<String,Object> properties=new HashMap<>();final Set<String> permissions=new HashSet<>();
        boolean commandFailure,threadFailure;
        Runnable commandHook;
        final Map<Values.WorldRef,Integer> clocks=new LinkedHashMap<>();
        @Override public Map<Values.WorldRef,Integer> worldTimes(){return Map.copyOf(clocks);}
        @Override public void requireExecutionThread(){if(threadFailure)throw new IllegalStateException("not the server thread");}
        @Override public boolean supportsEvent(String event){return Set.of("join","load","unload","quit","command","world change","teleport","inventory click","death of player","respawn").contains(event);}
        @Override public boolean supportsProperty(String name,boolean writable){return Set.of("name","uuid","world","location","inventory","tool","helmet","chestplate","leggings","boots","type","amount","balance","level","experience","flight mode","online","op").contains(name);}
        @Override public Object property(String property,Object subject,Context c){if(property.equals("all players"))return List.of("Alice","Bob");if(property.equals("uuid"))return subject+"-uuid";if(property.equals("name"))return subject;if(property.equals("world"))return new Values.WorldRef("test_dungeon1");if(property.equals("inventory"))return "inventory";return properties.getOrDefault(property+":"+subject,property.equals("op")?false:null);}
        @Override public void changeProperty(String property,Object subject,Object value,String mode,Context c){Object old=properties.get(property+":"+subject);properties.put(property+":"+subject,mode.equals("set")?value:Values.number(old)+(mode.equals("add")?1:-1)*Values.number(value));}
        @Override public Object literal(String name){return Set.of("air","elytra","paper").contains(name)?name:null;}
        @Override public String display(Object value){return value instanceof Values.WorldRef world?world.id():Values.text(value);}
        @Override public boolean hasPermission(Object p,String permission){return permissions.contains(permission);}
        @Override public boolean isPlayer(Object p){return Set.of("Alice","Bob").contains(p);}
        @Override public void send(Object recipient,String message){messages.add(recipient+":"+message);}
        @Override public void broadcast(String message){messages.add(message);}
        @Override public void command(Object sender,String command){if(commandFailure)throw new IllegalStateException("Economy failed");commands.add(command);if(commandHook!=null)commandHook.run();}
        @Override public void give(Object p,Object item){properties.put("given:"+p,item);}
        @Override public void teleport(Object p,Object destination){properties.put("location:"+p,destination);}
        @Override public void potion(Object p,String type,int level,Values.Span duration){potions.add(p+":"+type+":"+level+":"+duration.ticks());}
        @Override public Action compileEffect(String s,ExpressionCompiler ec){if(s.startsWith("close "))return c->properties.put("closed",true);return null;}
        @Override public void error(Syntax.Source source,Throwable failure){errors.add(source+":"+failure.getMessage());}
    }
    TestPlatform platform;Engine engine;
    @BeforeEach void setup(){platform=new TestPlatform();engine=new Engine(platform);}
    void install(String text){Program p=new Parser(platform).compile(Map.of("test.sk",text));assertTrue(p.files.get("test.sk").valid(),p.files.get("test.sk").diagnostics().toString());engine.install(p);}
    Object global(String name){return engine.variables().get(name,Map.of());}
    void join(){engine.dispatch("join",Map.of("player","Alice","sender","Alice"));}
    Context context(){return new Context(engine,Map.of("player","Alice"),new HashMap<>(),50000);}
    @Test void nativeFunctionEntryExecutesInstalledSourceAndCommits(){
        install("""
            function grant(n: number) :: number:
                add {_n} to {total}
                return {total}
            """);
        assertEquals(3d,engine.invokeFunction("grant",List.of(3d)));
        assertEquals(5d,engine.invokeFunction("grant",List.of(2d)));
        assertEquals(5d,global("total"));
    }
    @Test void nativeFunctionRejectsOffThreadEntryBeforeTouchingVariables(){
        TestPlatform guardedPlatform=new TestPlatform();
        Engine guarded=new Engine(guardedPlatform);
        guarded.install(new Parser(guarded.platform()).compile(Map.of("thread.sk","""
            function mutate():
                set {state} to true
            """)));
        guardedPlatform.threadFailure=true;
        assertThrows(IllegalStateException.class,()->guarded.invokeFunction("mutate",List.of()));
        assertNull(guarded.variables().get("state",Map.of()));
    }
    @Test void nativeFunctionEntryRollsBackNestedSourceFailures(){
        install("""
            function grant(n: number):
                set {lock} to true
                add {_n} to {total}
                execute console command "required-economy"
            """);
        engine.variables().set("total",7d,Map.of());platform.commandFailure=true;
        assertThrows(IllegalStateException.class,()->engine.invokeFunction("grant",List.of(2d)));
        assertEquals(7d,global("total"));assertNull(global("lock"));
    }
    @Test void localFunctionsArePrivateAndDoNotOverwriteOtherScripts(){
        Program pack=new Parser(platform).compile(Map.of("a.sk","""
            local function value() :: number:
                return 1
            on load:
                set {a} to value()
            ""","b.sk","""
            local function value() :: number:
                return 2
            on load:
                set {b} to value()
            """));
        assertTrue(pack.files.values().stream().allMatch(Program.File::valid));
        engine.install(pack);assertEquals(1d,global("a"));assertEquals(2d,global("b"));
        assertFalse(pack.functions.containsKey("value"));
        assertThrows(IllegalArgumentException.class,()->engine.invokeFunction("value",List.of()));
        Program caller=new Parser(platform).compile(Map.of("private.sk","""
            local function private_value() :: number:
                return 1
            ""","outside.sk","""
            on load:
                set {outside} to private_value()
            """));
        assertTrue(caller.files.get("private.sk").valid());assertFalse(caller.files.get("outside.sk").valid());
    }
    @Test void duplicateGlobalsAreRejectedInsteadOfSelectingLastFile(){
        Program pack=new Parser(platform).compile(Map.of("a.sk","function shared() :: number:\n    return 1", "b.sk","function shared() :: number:\n    return 2"));
        assertTrue(pack.functions.isEmpty());
        assertTrue(pack.files.values().stream().allMatch(f->f.diagnostics().stream().anyMatch(d->d.message().contains("Duplicate global"))));
    }
    @Test void functionReferencesInPlainTextDoNotDisableValidFiles(){
        Program pack=new Parser(platform).compile(Map.of("failed.sk","""
            function unavailable():
                unsupported effect
            ""","text.sk","""
            on load:
                set {text} to "unavailable()"
            ""","caller.sk","""
            on load:
                unavailable()
            """));
        assertFalse(pack.files.get("failed.sk").valid());assertFalse(pack.files.get("caller.sk").valid());
        assertTrue(pack.files.get("text.sk").valid());engine.install(pack);assertEquals("unavailable()",global("text"));
    }
    @Test void standaloneConditionsEndOnlyTheirCurrentSection(){
        install("""
            on join:
                loop 3 times:
                    loop-number is greater than 1
                    add loop-number to {sum}
                if true is true:
                    player has permission "required"
                    set {denied} to true
                set {after} to "this is text"
                true is false
                set {unreachable} to true
            """);
        join();assertEquals(5d,global("sum"));assertNull(global("denied"));
        assertEquals("this is text",global("after"));assertNull(global("unreachable"));
    }
    @Test void doWhileRunsOnceThenEvaluatesTheConditionAfterDelays(){
        install("""
            on join:
                do while true is false:
                    add 1 to {once}
                set {_n} to 0
                do while {_n} is less than 3:
                    wait 1 tick
                    add 1 to {_n}
                    add 1 to {count}
                set {finished} to true
            """);
        join();assertEquals(1d,global("once"));assertNull(global("finished"));
        for(int i=0;i<3;i++)engine.tick();assertEquals(3d,global("count"));assertEquals(true,global("finished"));
    }
    @Test void worldClockTriggersCrossMidnightAndDoNotReplaySkippedTime(){
        var world=new Values.WorldRef("world");platform.clocks.put(world,23995);
        install("""
            at 6:00:
                add 1 to {dawn}
                set {world} to event-world
            at 7:00:
                add 1 to {morning}
            at 13:00:
                add 1 to {afternoon}
            """);
        platform.clocks.put(world,5);for(int i=0;i<10;i++)engine.tick();
        assertEquals(1d,global("dawn"));assertEquals(world,global("world"));
        platform.clocks.put(world,7000);for(int i=0;i<10;i++)engine.tick();
        assertNull(global("morning"));assertEquals(1d,global("afternoon"));
        for(int i=0;i<20;i++)engine.tick();assertEquals(1d,global("afternoon"));
        engine.install(new Program(Map.of()));platform.clocks.put(world,0);
        for(int i=0;i<10;i++)engine.tick();assertEquals(1d,global("dawn"));
    }
    @Test void clockLiteralsPreserveMinecraftSixHourOffsetAndRejectInvalidTimes(){
        assertEquals(19750,Values.GameTime.parse("1:45").ticks());
        assertEquals(17,Values.GameTime.parse("6:01").ticks());
        assertEquals("6:01",Values.GameTime.parse("6:01").toString());
        assertThrows(IllegalArgumentException.class,()->Values.GameTime.parse("24:00"));
        assertThrows(IllegalArgumentException.class,()->Values.GameTime.parse("7:60"));
        Program invalid=new Parser(platform).compile(Map.of("clock.sk","at 24:00:\n    set {invalid} to true"));
        assertFalse(invalid.files.get("clock.sk").valid());
    }
    @Test void consoleOnlyCommandsRejectPlayersEvenWhenTheyHavePermission(){
        install("""
            command /consoleonly:
                permission: test.allowed
                executable by: console
                trigger:
                    add 1 to {commands}
            """);
        var command=engine.program().files.get("test.sk").commands().getFirst();
        platform.permissions.add("test.allowed");
        assertFalse(engine.command(command,"Alice",List.of()));assertNull(global("commands"));
        assertTrue(engine.command(command,"Console",List.of()));assertEquals(1d,global("commands"));
    }
    @Test void invalidBareBooleanConditionsMatchUpstreamRejection(){
        for(String section:List.of("if true","while true","do while false")){
            Program program=new Parser(platform).compile(Map.of("invalid.sk","on join:\n    "+section+":\n        set {invalid} to true"));
            assertFalse(program.files.get("invalid.sk").valid(),section);
        }
    }
    @Test void reloadDuringPeriodicExecutionCancelsOldFramesAndTimers(){
        install("""
            every 1 tick:
                execute console command "reload"
                set {stale-frame} to true
            every 1 tick:
                set {stale-timer} to true
            """);
        Program next=new Parser(platform).compile(Map.of("new.sk","on load:\n    set {fresh} to true"));
        platform.commandHook=()->engine.install(next);
        assertDoesNotThrow(engine::tick);assertEquals(true,global("fresh"));
        assertNull(global("stale-frame"));assertNull(global("stale-timer"));
    }
    @Test void reloadDuringWorldClockExecutionDoesNotUseStaleClockHandlers(){
        var world=new Values.WorldRef("world");platform.clocks.put(world,995);
        install("""
            at 7:00:
                execute console command "reload"
            at 7:00:
                set {stale-clock} to true
            """);
        platform.commandHook=()->engine.install(new Program(Map.of()));
        platform.clocks.put(world,1000);
        assertDoesNotThrow(()->{for(int i=0;i<10;i++)engine.tick();});assertNull(global("stale-clock"));
    }
    @Test void reloadDuringAnEventDoesNotExecuteUnloadedHandlers(){
        install("""
            on join:
                execute console command "reload"
            on join:
                set {stale-event} to true
            """);
        platform.commandHook=()->engine.install(new Program(Map.of()));join();assertNull(global("stale-event"));
    }
    @Test void delayedNestedLoopsKeepScopeAndOrder(){
        install("""
            on join:
                set {_prefix} to "A"
                loop 2 times:
                    set {_outer} to loop-number
                    loop 2 times:
                        wait 1 tick
                        add "%{_prefix}%%{_outer}%-%loop-number%" to {result::*}
                set {done} to true
            """);join();assertNull(global("done"));for(int i=0;i<4;i++)engine.tick();assertEquals(List.of("A1-1","A1-2","A2-1","A2-2"),global("result::*"));assertEquals(true,global("done"));assertTrue(platform.errors.isEmpty(),platform.errors.toString());
    }
    @Test void nestedTypedLoopsNumberOuterThenInner(){
        install("""
            on join:
                loop all players:
                    loop all players:
                        add "%loop-player-1%:%loop-player-2%:%loop-player%" to {pairs::*}
            """);join();assertEquals(List.of("Alice:Alice:Alice","Alice:Bob:Bob","Bob:Alice:Alice","Bob:Bob:Bob"),global("pairs::*"));
    }
    @Test void datesSubtractDurationsAndDamageChangesUseEventState(){
        install("""
            on join:
                set {_last} to now - 31 seconds
                set {elapsed} to difference between now and {_last}
                set damage to damage * 0.4
                set {after} to damage
            """);Map<String,Object> state=engine.dispatch("join",Map.of("player","Alice","damage",10d));assertEquals(4d,state.get("damage"));assertEquals(4d,global("after"));assertEquals(31000d,((Values.Span)global("elapsed")).milliseconds(),100);
    }
    @Test void structuredVariablesInterpolateAndKeepIndicesOnRemove(){
        install("""
            on join:
                set {members::%uuid of player%::*} to "one", "two", "three"
                remove "two" from {members::%uuid of player%::*}
                loop {members::%uuid of player%::*}:
                    add "%loop-index%:%loop-value%" to {indices::*}
            """);join();assertEquals(List.of("1:one","3:three"),global("indices::*"));
    }
    @Test void localFunctionScopeAndTrueShortCircuit(){
        install("""
            function bump(v: number) :: number:
                set {_v} to {_v} + 2
                return {_v}
            function explode() :: boolean:
                execute console command "fail"
                return true
            on join:
                set {_v} to 5
                set {result} to bump({_v})
                set {caller} to {_v}
                if true is true or explode():
                    set {short} to true
            """);platform.commandFailure=true;join();assertEquals(7d,global("result"));assertEquals(5d,global("caller"));assertEquals(true,global("short"));assertTrue(platform.errors.isEmpty());
    }
    @Test void failureInsideFunctionAbortsReward(){
        install("""
            function debit():
                execute console command "fail"
            on join:
                debit()
                set {reward} to true
            """);platform.commandFailure=true;join();assertNull(global("reward"));assertEquals(1,platform.errors.size());
    }
    @Test void synchronousCancellationSharedAcrossHandlersButNotDelayed(){
        install("""
            on command:
                cancel event
            on command:
                set {observed} to true
                wait 1 tick
                uncancel event
            """);Map<String,Object> state=engine.dispatch("command",Map.of());assertEquals(true,state.get("cancelled"));engine.tick();assertEquals(true,state.get("cancelled"));assertEquals(1,platform.errors.size());
    }
    @Test void reloadInvalidatesWaitsWithoutErasingPersistentVariables(){
        install("""
            on join:
                set {persist} to 42
                wait 10 ticks
                set {stale} to true
            """);join();install("on join:\n    set {new} to true\n");for(int i=0;i<11;i++)engine.tick();assertNull(global("stale"));assertEquals(42d,global("persist"));join();assertEquals(true,global("new"));
    }
    @Test void unknownSyntaxDisablesWholeFileAndDependentCallers(){
        Program p=new Parser(platform).compile(Map.of("broken.sk","function unavailable():\n    invent fake effect\n","caller.sk","on join:\n    unavailable()\n    set {fake} to true\n"));assertFalse(p.files.get("broken.sk").valid());assertFalse(p.files.get("caller.sk").valid());engine.install(p);join();assertNull(global("fake"));assertEquals(2,p.files.get("broken.sk").diagnostics().getFirst().source().line());
    }
    @Test void infiniteLoopStopsUnderBudget(){install("on join:\n    while true is true:\n        add 1 to {count}\n");join();assertEquals(1,platform.errors.size());assertTrue(platform.errors.getFirst().contains("budget"));assertNull(global("count"));}
    @Test void failedCommandRestoresLocksAndSparseGlobalVariables(){
        engine.variables().set("members::1","first",Map.of());engine.variables().set("members::3","third",Map.of());
        install("""
            function begin() :: boolean:
                set {class.lock} to true
                return true
            on join:
                if begin():
                    delete {members::*}
                    set {class.last} to now
                    execute console command "fail"
                    delete {class.lock}
            """);platform.commandFailure=true;join();assertNull(global("class.lock"));assertNull(global("class.last"));assertEquals(Map.of("1","first","3","third"),engine.variables().entries("members::*",Map.of()));assertEquals(1,platform.errors.size());
    }
    @Test void structuredListsAppendAfterSparseIndicesAndKeepAdjacentNamespaces(){
        var v=engine.variables();
        v.set("members::1","one",Map.of());v.set("members::10","ten",Map.of());v.set("members::01","leading",Map.of());v.set("members::label","named",Map.of());
        v.set("membersExtra::1","separate",Map.of());v.set("members::nested::1","nested",Map.of());
        v.add("members::*","eleven",Map.of());
        assertEquals(List.of("01","1","10","11","label"),new ArrayList<>(v.entries("members::*",Map.of()).keySet()));
        assertEquals("eleven",v.get("members::11",Map.of()));
        var before=v.capture("members::*");v.set("members::*",List.of("replacement"),Map.of());v.restore("members::*",before);
        assertEquals("nested",v.get("members::nested::1",Map.of()));v.set("members::*",null,Map.of());
        assertNull(v.get("members::nested::1",Map.of()));assertEquals("separate",v.get("membersExtra::1",Map.of()));
    }
    @Test void invalidGlobalListIsRejectedBeforeExistingValuesAreReplaced(){
        var v=engine.variables();v.set("values::*",List.of(1d,2d),Map.of());
        v.validateWith(value->{if(value instanceof Number n&&!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Non-finite");});
        assertThrows(IllegalArgumentException.class,()->v.set("values::*",List.of(3d,Double.NaN),Map.of()));
        assertEquals(List.of(1d,2d),v.get("values::*",Map.of()));
    }
    @Test void delayedFunctionFailuresAreDiagnosedAndDoNotEscapeServerTick(){
        install("""
            function later():
                wait 1 tick
                set {partial} to true
                execute console command "fail"
            on join:
                later()
                set {callerDone} to true
            """);platform.commandFailure=true;join();assertDoesNotThrow(()->engine.tick());assertNull(global("partial"));assertEquals(true,global("callerDone"));assertEquals(1,platform.errors.size());
    }
    @Test void temporalComparisonUsesMillisecondsAndTickWaits(){
        engine.clock(()->100000);
        install("""
            on join:
                set {then} to now
                wait 2 ticks
                set {age} to difference between now and {then}
                if {age} < 1 second:
                    set {recent} to true
            """);join();engine.clock(()->100100);engine.tick();assertNull(global("recent"));engine.tick();assertEquals(true,global("recent"));assertEquals(new Values.Span(100),global("age"));
    }
    @Test void listSplitAndAlternateReplacement(){
        install("""
            on join:
                set {_class} to "death knight"
                replace all " " with "_" in {_class}
                set {_parts::*} to {_class} split at "_"
                set {class} to "%{_parts::1}%:%{_parts::2}%"
            """);join();assertEquals("death:knight",global("class"));
    }
    @Test void originalDeathScriptPreservesInventoryAndChargesConfiguredLoss()throws Exception {
        installOriginal("sv_death_penalty.sk");platform.properties.put("balance:Alice",10000d);platform.properties.put("online:Alice",true);platform.properties.put("level:Alice",12d);platform.properties.put("experience:Alice",.5d);
        Map<String,Object> death=engine.dispatch("death of player",Map.of("victim","Alice","player","Alice"));assertEquals(true,death.get("keep inventory"));assertEquals(true,death.get("cancel drops"));assertEquals("",death.get("death message"));assertEquals(0d,platform.properties.get("level:Alice"));
        engine.dispatch("respawn",Map.of("player","Alice"));for(int i=0;i<3;i++)engine.tick();assertEquals(9000d,platform.properties.get("balance:Alice"));assertEquals(List.of("warp spawn Alice"),platform.commands);assertNull(global("sv.death.pending::Alice-uuid"));assertNotNull(global("sv.death.no_back::Alice-uuid"));assertTrue(platform.errors.isEmpty(),platform.errors.toString());
    }
    private String source(String name)throws Exception {String root=System.getProperty("suppliedScripts");Assumptions.assumeTrue(root!=null,"Run with -PsuppliedScripts=<original script directory>");return Files.readString(Path.of(root,name));}
    private void installOriginal(String name)throws Exception {Program p=new Parser(platform).compile(Map.of(name,source(name)));assertTrue(p.files.get(name).valid(),p.files.get(name).diagnostics().toString());engine.install(p);}
    @Test void originalClassScriptExecutesFunctionsAndLocks()throws Exception {
        installOriginal("sv_class_switch.sk");assertEquals("Dragonborn",engine.call("svclass_name",List.of("dragonborn"),context()));assertEquals(true,engine.call("svclass_valid",List.of("water_samurai"),context()));assertEquals(false,engine.call("svclass_valid",List.of("invented"),context()));assertEquals(false,engine.call("svclass_unlocked",List.of("Alice","dragonborn"),context()));platform.permissions.add("svframe.supporter");assertEquals(true,engine.call("svclass_unlocked",List.of("Alice","dragonborn"),context()));assertEquals(true,engine.call("svclass_begin",List.of("Alice"),context()));assertEquals(false,engine.call("svclass_begin",List.of("Alice"),context()));engine.call("svclass_end",List.of("Alice"),context());assertEquals(true,engine.call("svclass_begin",List.of("Alice"),context()));
    }
    @Test void originalSecurityScriptCancelsForbiddenButKeepsQol()throws Exception {
        installOriginal("sv_security_command_guard.sk");for(String command:List.of("tp 0 70 0","minecraft:tp Alice 0 70 0","pl","mvcreate bad")){assertEquals(true,engine.dispatch("command",Map.of("player","Alice","sender","Alice","full command",command)).get("cancelled"),command);}assertEquals(false,engine.dispatch("command",Map.of("player","Alice","sender","Alice","full command","warp spawn")).get("cancelled"));platform.properties.put("op:Alice",true);assertEquals(false,engine.dispatch("command",Map.of("sender","Alice","full command","tp 0 70 0")).get("cancelled"));assertTrue(platform.errors.isEmpty(),platform.errors.toString());
    }
    @Test void originalWolvenScriptAppliesSaturationOnlyWithAllPieces()throws Exception {
        installOriginal("sv_wolven_set_bonus.sk");platform.properties.put("chestplate:Alice","Grandmaster Wolven Chestplate");platform.properties.put("leggings:Alice","Grandmaster Wolven Leggings");platform.properties.put("boots:Alice","Grandmaster Wolven Boots");for(int i=0;i<100;i++)engine.tick();assertEquals(List.of("Alice:saturation:1:120"),platform.potions);
    }
    @Test void originalWorldLockRunsDelayedFunctionAndPeriodicClamp()throws Exception {
        installOriginal("sv_rpg_world_lock.sk");platform.properties.put("flight mode:Alice",true);platform.properties.put("chestplate:Alice","elytra");join();for(int i=0;i<10;i++)engine.tick();assertEquals(false,platform.properties.get("flight mode:Alice"));assertEquals("air",platform.properties.get("chestplate:Alice"));assertEquals("elytra",platform.properties.get("given:Alice"));assertTrue(platform.errors.isEmpty(),platform.errors.toString());
    }
}
