package vn.svframe.fantasyhub.core.hero;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class HeroResourceAttributesTest {
 @Test void resourcesUseNativeStatsAndEachAllocationCapsAtFifty()throws Exception{
  var files=HeroClassFiles.generate(HeroCatalog.defaults());
  assertTrue(files.containsKey("SVFrameMMO/attributes/hero-resources.yml"));
  var attributes=new Yaml().<Map<String,Map<String,Object>>>load(new String(files.get("SVFrameMMO/attributes/hero-resources.yml"),java.nio.charset.StandardCharsets.UTF_8));
  assertEquals(2,attributes.size());
  for(String key:java.util.List.of("mana","stamina")){
   var row=attributes.get(key);assertEquals(50,row.get("max-points"));assertEquals(5,((Map<?,?>)row.get("buff")).get("max_"+key));
  }
 }
 @Test void portalPersistsAndIsLimitedToFifteenBlocks()throws Exception{
  var portal=HeroCatalog.defaults().ability("SORCERER_SUPREME_SLING_RING");assertEquals(15,portal.range());assertTrue(portal.duration()>=15);
 }
}
