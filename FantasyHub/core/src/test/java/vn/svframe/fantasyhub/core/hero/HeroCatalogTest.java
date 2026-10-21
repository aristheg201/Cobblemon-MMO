package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
class HeroCatalogTest {
  @Test void sixClassesHaveSevenDistinctActivesAndCorrectResourceOwners() throws Exception {
    var c=HeroCatalog.defaults();assertEquals(6,c.heroes().size());assertEquals(43,c.abilities().size());
    for(var h:c.heroes().values()) {
      assertEquals(h.id().equals("THOR")?8:7,h.abilities().size());
      for(int i=0;i<7;i++)assertEquals(i+1,h.abilities().get(i).slot());
    }
    assertEquals("STAMINA",c.hero("SPIDER_MAN").resource());
    assertEquals("STAMINA",c.hero("BATMAN").resource());
    assertEquals(500,c.hero("SORCERER_SUPREME").maximum());
    assertEquals("MANA",c.hero("IRON_MAN").resource());
    assertFalse(c.abilities().keySet().stream().anyMatch(k->k.contains("HENSHIN")));
  }
  @Test void catalogPinsTheSuppliedMultiHitAndUltimateNumbers() throws Exception {
    var c=HeroCatalog.defaults();var b=c.ability("SPIDER_MAN_WEB_SHOOTER_BARRAGE");
    assertEquals(10,b.cooldown());assertEquals(25,b.cost());assertEquals(8,b.phases().getFirst().hits());
    assertEquals(2.4,b.totalCoefficient(),1e-9);
    var u=c.ability("SPIDER_MAN_WORLD_WEB_TRAP");assertEquals(7.8,u.totalCoefficient(),1e-9);assertTrue(u.ultimate());
    assertEquals(3.8,c.ability("IRON_MAN_UNIBEAM").totalCoefficient(),1e-9);
    assertEquals(100,c.ability("SORCERER_SUPREME_SLING_RING").cost());
    assertEquals(2,c.ability("FLASH_SUPER_SPEED").upkeep());
    assertEquals(3,c.ability("SPIDER_MAN_COCOON_SLAM").recast());
  }
  @Test void malformedOrIncompleteConfigurationCannotInstallPartialClasses() throws Exception {
    var root=HeroCatalog.defaultJson();root.getAsJsonArray("heroes").get(0).getAsJsonObject().addProperty("maximum",Double.NaN);
    var malformed=root;assertThrows(IllegalArgumentException.class,()->HeroCatalog.parse(malformed));
    root=HeroCatalog.defaultJson();root.getAsJsonArray("heroes").get(0).getAsJsonObject().getAsJsonArray("abilities").remove(0);
    var broken=root;assertThrows(IllegalArgumentException.class,()->HeroCatalog.parse(broken));
  }
  @Test void everyCooldownAndCostMatchesTheUsersBaseSpecification() throws Exception {
    var c=HeroCatalog.defaults();
    var ids=java.util.List.of("SPIDER_MAN","SORCERER_SUPREME","IRON_MAN","THOR","FLASH","BATMAN");
    double[][] cd={{8,12,10,15,18,20,60},{6,12,16,25,20,22,70},{3,9,11,13,18,30,65},{7,12,10,14,22,35,65},{1,15,8,12,18,20,60},{5,9,16,20,18,12,60}};
    double[][] cost={{15,20,25,30,35,20,60},{40,60,90,80,100,110,200},{10,25,20,15,45,50,80},{15,20,15,25,30,50,80},{0,20,15,25,30,25,70},{10,15,20,25,20,25,50}};
    for(int h=0;h<ids.size();h++)for(int slot=0;slot<7;slot++) {
      var a=c.hero(ids.get(h)).abilities().get(slot);
      assertEquals(cd[h][slot],a.cooldown(),a.id());assertEquals(cost[h][slot],a.cost(),a.id());
      assertTrue(a.cost()<=c.hero(ids.get(h)).maximum(),a.id());
    }
  }

}
