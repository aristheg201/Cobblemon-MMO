package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.util.*;
class HeroEquipmentTest {
  @Test void sixCompleteOutfitsHaveEqualBudgetsAndNoTransformationRules() throws Exception {
    var c=HeroCatalog.defaults();var files=HeroEquipmentFiles.generate(c);
    var items=(Map<String,Map<String,Object>>)new Yaml().load(new String(files.get("SVFrameItems/items/heroes.yml"),java.nio.charset.StandardCharsets.UTF_8));
    assertEquals(22,items.size());
    for(var hero:c.heroes().keySet()) {
      var sum=new HashMap<String,Double>();
      for(var item:items.entrySet())if(item.getKey().startsWith("HERO_"+hero+"_")) {
        var stats=(Map<String,Map<String,Object>>)item.getValue().get("stats");stats.forEach((key,value)->sum.merge(key,((Number)value.get("value")).doubleValue(),Double::sum));
      }
      assertEquals(14,sum.get("armor"),.0001);assertEquals(12,sum.get("max-health"),.0001);assertEquals(2,sum.get("attack-damage"),.0001);
    }
    assertFalse(new String(files.get("SVFrameItems/rules/heroes.yml")).toLowerCase(Locale.ROOT).contains("henshin"));
  }
}
