package vn.svframe.fantasyhub.core;
import com.google.gson.*;
import java.util.*;
/** Repairs only the known shipped Rider sets; custom admin equipment/prices remain intact. */
final class CommerceCatalogMigration {
 private static final Map<String,List<String>> WEAPONS=Map.of(
  "AGITO",List.of("AGITO_FLAME_SABER","AGITO_STORM_HALBERD","AGITO_SHINING_CALIBER"),
  "KABUTO",List.of("KABUTO_KUNAI_GUN"),"DECADE",List.of("DECADE_RIDE_BOOKER_SWORD","DECADE_RIDE_BOOKER_GUN"),
  "RYUKI",List.of("RYUKI_DRAG_SABER"),"KNIGHT",List.of("KNIGHT_DARK_VISOR","KNIGHT_DARK_BLADE"),"ZOLDA",List.of("ZOLDA_MAGNA_VISOR","ZOLDA_GIGA_LAUNCHER"));
 private static String belt(String id){return "rpg:"+(id.equals("KABUTO")?"KABUTO_ZECTER_BELT":id+"_BELT");}
 private static List<String> gear(String id,boolean modern){
  var items=new ArrayList<String>();String armor=id.equals("AGITO")?"AGITO_GROUND":id;for(String slot:List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS"))items.add("rpg:"+armor+"_"+slot);
  if(modern){items.add(belt(id));for(String weapon:WEAPONS.get(id))items.add("rpg:"+weapon);}
  else{if(id.equals("KABUTO"))items.add("rpg:KABUTO_KUNAI_GUN");if(!id.equals("AGITO"))items.add(belt(id));items.add("rpg:trailblazer_blade");}return items;
 }
 private static boolean matches(JsonArray items,List<String> expected){
  if(items==null||items.size()!=expected.size())return false;
  for(int i=0;i<items.size();i++){var item=items.get(i).getAsJsonObject();if(!Set.of("item","count").containsAll(item.keySet())||!item.has("item")||!expected.get(i).equals(item.get("item").getAsString())||(item.has("count")?item.get("count").getAsInt():1)!=1)return false;}return true;
 }
 static boolean upgrade(JsonObject catalog){
  if(catalog.has("version")&&catalog.get("version").getAsInt()>=2)return false;
  var equipment=catalog.getAsJsonObject("equipment");if(equipment==null)return false;boolean changed=false;
  for(String id:WEAPONS.keySet())if(matches(equipment.getAsJsonArray(id),gear(id,false))){var replacement=new JsonArray();for(String item:gear(id,true)){var reward=new JsonObject();reward.addProperty("item",item);reward.addProperty("count",1);replacement.add(reward);}equipment.add(id,replacement);changed=true;}
  if(changed)catalog.addProperty("version",2);return changed;
 }
}
