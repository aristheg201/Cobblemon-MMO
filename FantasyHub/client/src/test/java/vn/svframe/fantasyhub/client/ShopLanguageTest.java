package vn.svframe.fantasyhub.client;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import com.google.gson.*;import java.nio.file.*;
class ShopLanguageTest {
 @Test void bothLocalesCoverRealCatalogClassesOffersAndRegisteredAgitoItems() throws Exception {
  var catalog=JsonParser.parseString(Files.readString(Path.of("../core/src/main/resources/default/commerce.json"))).getAsJsonObject();
  for(String locale:new String[]{"en_us","vi_vn"}){
   var lang=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/fantasyhub/lang/"+locale+".json"))).getAsJsonObject();
   for(var id:catalog.getAsJsonObject("classTiers").keySet())assertTrue(lang.has("fantasyhub.commerce.class."+id.toLowerCase(java.util.Locale.ROOT)),locale+" "+id);
   for(var row:catalog.getAsJsonArray("offers"))for(String field:new String[]{"name","description"})assertTrue(lang.has("fantasyhub.commerce.offer."+row.getAsJsonObject().get("id").getAsString()+"."+field));
   for(String part:new String[]{"helmet","chestplate","leggings","boots"}){String key="item.fantasyhub.agito_ground_"+part;assertTrue(lang.has(key));assertFalse(lang.get(key).getAsString().startsWith("item."));}
   for(String stat:new String[]{"attack_damage","mana_regeneration"})assertTrue(lang.has("svframeitems.stat."+stat),locale+" "+stat);
   for(String tab:new String[]{"class","shop","bundle"})assertEquals(tab.toUpperCase(java.util.Locale.ROOT),lang.get("fantasyhub.commerce.tab_label."+tab).getAsString());
  }
 }
}
