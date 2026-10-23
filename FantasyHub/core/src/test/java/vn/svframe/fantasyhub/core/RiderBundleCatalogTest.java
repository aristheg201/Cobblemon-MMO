package vn.svframe.fantasyhub.core;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RiderBundleCatalogTest {
 private JsonObject catalog(String file)throws Exception{return JsonParser.parseString(Files.readString(Path.of(file))).getAsJsonObject();}
 private JsonObject shipped()throws Exception{return catalog("src/main/resources/default/commerce.json");}
 private JsonObject old()throws Exception{return catalog("src/test/resources/commerce-riders-v1.json");}
 @Test void allSixRiderBundlesContainTheirActualBeltAndWeaponsExactlyOnce()throws Exception{
  var gear=shipped().getAsJsonObject("equipment");
  Map.of("AGITO",List.of("AGITO_BELT","AGITO_FLAME_SABER","AGITO_STORM_HALBERD","AGITO_SHINING_CALIBER"),"KABUTO",List.of("KABUTO_ZECTER_BELT","KABUTO_KUNAI_GUN"),"DECADE",List.of("DECADE_BELT","DECADE_RIDE_BOOKER_SWORD","DECADE_RIDE_BOOKER_GUN"),"RYUKI",List.of("RYUKI_BELT","RYUKI_DRAG_SABER"),"KNIGHT",List.of("KNIGHT_BELT","KNIGHT_DARK_VISOR","KNIGHT_DARK_BLADE"),"ZOLDA",List.of("ZOLDA_BELT","ZOLDA_MAGNA_VISOR","ZOLDA_GIGA_LAUNCHER")).forEach((id,required)->{
   var items=gear.getAsJsonArray(id);for(String item:required)assertEquals(1,java.util.stream.StreamSupport.stream(items.spliterator(),false).filter(v->v.getAsJsonObject().get("item").getAsString().equals("rpg:"+item)).count(),id+" missing/duplicated "+item);
   assertFalse(items.toString().contains("trailblazer_blade"),id+" must use authored Rider weapons");
  });
 }
 @Test void allShippedLegacyRiderSetsUpgradeOnceWithoutChangingPricesOrOtherRewards()throws Exception{
  var c=old();var prices=shipped().get("tiers");c.add("tiers",prices.deepCopy());var custom=JsonParser.parseString("[{\"item\":\"minecraft:paper\",\"count\":2}]");c.getAsJsonObject("equipment").add("ADMIN_SET",custom.deepCopy());c.addProperty("adminNote","preserve");
  assertTrue(CommerceCatalogMigration.upgrade(c));assertEquals(prices,c.get("tiers"));assertEquals(custom,c.getAsJsonObject("equipment").get("ADMIN_SET"));assertEquals("preserve",c.get("adminNote").getAsString());assertFalse(CommerceCatalogMigration.upgrade(c));var expected=shipped().getAsJsonObject("equipment");for(String id:List.of("AGITO","KABUTO","DECADE","RYUKI","KNIGHT","ZOLDA"))assertEquals(expected.get(id),c.getAsJsonObject("equipment").get(id),id);
 }
 @Test void legacyRewardsWithImplicitCountOneRemainValid()throws Exception{
  var c=old();for(var item:c.getAsJsonObject("equipment").getAsJsonArray("AGITO"))item.getAsJsonObject().remove("count");assertTrue(CommerceCatalogMigration.upgrade(c));assertEquals(shipped().getAsJsonObject("equipment").get("AGITO"),c.getAsJsonObject("equipment").get("AGITO"));
 }
 @Test void customAgitoEquipmentIsPreserved()throws Exception{
  var c=old();var custom=new JsonArray();custom.add(JsonParser.parseString("{\"item\":\"minecraft:paper\",\"count\":2}"));c.getAsJsonObject("equipment").add("AGITO",custom.deepCopy());CommerceCatalogMigration.upgrade(c);assertEquals(custom,c.getAsJsonObject("equipment").get("AGITO"));
 }
}
