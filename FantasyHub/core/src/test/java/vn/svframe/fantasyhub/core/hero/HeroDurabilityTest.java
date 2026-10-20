package vn.svframe.fantasyhub.core.hero;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class HeroDurabilityTest {
  @Test void rpgStormbreakerNeverConsumesVanillaDurability() throws Exception {
    var bytes=HeroEquipmentFiles.generate(HeroCatalog.defaults()).get("SVFrameItems/rules/heroes-weapons.yml");
    Map<String,Object> rules=new Yaml().load(new String(bytes,StandardCharsets.UTF_8));
    assertEquals(Boolean.TRUE,((Map<?,?>)rules.get("HERO_THOR_STORMBREAKER")).get("unbreakable"));
  }
}
