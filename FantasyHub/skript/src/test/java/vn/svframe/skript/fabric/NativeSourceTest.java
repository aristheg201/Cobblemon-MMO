package vn.svframe.skript.fabric;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import vn.svframe.skript.lang.*;

class NativeSourceTest {
    @Test void originalRequiredScriptsCompileWithoutChangingSource() throws Exception {
        String root=System.getProperty("suppliedScripts");Assumptions.assumeTrue(root!=null);
        Path scripts=Path.of(root),amk=scripts.getParent().getParent().getParent().resolve("amk/Skript/scripts/sv_anti_mage_knight.sk");
        Map<String,String> original=new LinkedHashMap<>();
        for(String file:List.of("sv_class_switch.sk","sv_dash_mmocore.sk"))original.put(file,Files.readString(scripts.resolve(file)));
        original.put("sv_anti_mage_knight.sk",Files.readString(amk));
        Program program=new Parser(new FabricPlatform()).compile(original);
        for(var file:program.files.values())assertTrue(file.valid(),file.diagnostics().toString());
        assertEquals(3,program.files.size());
    }
    @Test void vectorChainsMutateReceiverAndClonesRemainIndependent(){
        Engine engine=new Engine(new FabricPlatform(){@Override public void trace(Syntax.Source source,String kind,Platform.Context context){}@Override public void error(Syntax.Source source,Throwable error){throw new AssertionError(source.toString(),error);}});Program program=new Parser(engine.platform()).compile(Map.of("vector.sk","""
            on load:
                set {_v} to new Vector(3, 0, 4)
                set {_clone} to {_v}.clone()
                set {_unit} to {_v}.normalize()
                {_unit}.setY(0.25)
                set {x} to {_unit}.getX()
                set {y} to {_unit}.getY()
                set {originalX} to {_clone}.getX()
                set {dot} to {_unit}.dot(new Vector(1, 0, 0))
            """));assertTrue(program.files.get("vector.sk").valid(),program.files.get("vector.sk").diagnostics().toString());engine.install(program);
        assertEquals(.6,(double)engine.variables().get("x",Map.of()),1e-8);assertEquals(.25,engine.variables().get("y",Map.of()));assertEquals(3d,engine.variables().get("originalX",Map.of()));assertEquals(.6,(double)engine.variables().get("dot",Map.of()),1e-8);
    }
    @Test void originalRingStyleCoordinateChangesKeepTheCenterUnchanged(){
        Engine engine=new Engine(new FabricPlatform(){@Override public void trace(Syntax.Source source,String kind,Platform.Context context){}@Override public void error(Syntax.Source source,Throwable error){throw new AssertionError(source.toString(),error);}});
        var center=new Values.Location(new Values.WorldRef("minecraft:overworld"),10,64,20,0,0);engine.variables().set("center",center,Map.of());
        Program program=new Parser(engine.platform()).compile(Map.of("ring.sk","""
            on load:
                set {_loc} to {center}.clone()
                add 3 to x-coordinate of {_loc}
                add 0.12 to y-coordinate of {_loc}
                {_loc}.add(new Vector(0, 0, -2))
                set {result} to {_loc}
            """));assertTrue(program.files.get("ring.sk").valid(),program.files.get("ring.sk").diagnostics().toString());engine.install(program);
        assertEquals(center,engine.variables().get("center",Map.of()));assertEquals(new Values.Location(center.world(),13,64.12,18,0,0),engine.variables().get("result",Map.of()));
    }
}
