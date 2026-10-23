package vn.svframe.fantasyhub.core;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class StartupContractTest {
 @Test void normalProtocolClassIsOutsideMixinImplementationPackage() throws Exception {
  var c=JsonParser.parseString(Files.readString(Path.of("../protocol/src/main/resources/fantasyhub-cosmic.mixins.json"))).getAsJsonObject();
  assertFalse("vn.svframe.fantasyhub.protocol.Protocol".startsWith(c.get("package").getAsString()+"."));
 }
 @Test void itemBootstrapRunsThroughMmoPreDefinitionHook() throws Exception {
  var c=JsonParser.parseString(Files.readString(Path.of("src/main/resources/fabric.mod.json"))).getAsJsonObject();
  assertTrue(c.getAsJsonObject("entrypoints").has("svframemmo-skill-source"));
 }
}
