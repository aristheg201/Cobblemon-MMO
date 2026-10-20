package vn.svframe.fantasyhub.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HeroMjolnirModelTest {
 @Test void originalHierarchyAndPerFaceUvsAreDecoded(){
  var model=HeroBedrockModel.load("/assets/fantasyhub/native_geometry/heroes/soulslike_mjolnir.geo.json");
  assertTrue(model.nodes().containsKey("main"));assertEquals("main",model.parents().get("hammer"));
  assertTrue(model.nodes().size()>5);assertTrue(model.nodes().values().stream().flatMap(n->n.children.stream()).mapToInt(n->n.faces.size()).sum()>30);
  assertNotEquals(0,model.nodes().get("main").transform.roll);
 }
}
