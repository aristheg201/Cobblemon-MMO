package vn.svframe.fantasyhub.core.hero;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.yaml.snakeyaml.Yaml;

/** Ordinary stat gear. Unmasked three-piece suits share the same complete outfit budget. */
public final class HeroEquipmentFiles {
  private HeroEquipmentFiles(){}
  public static Map<String,byte[]> generate(HeroCatalog catalog) {
    var items=new LinkedHashMap<String,Object>();var rules=new LinkedHashMap<String,Object>();
    for(var hero:catalog.heroes().values()) {
      boolean unmasked=Set.of("THOR","SORCERER_SUPREME").contains(hero.id());
      var parts=unmasked?List.of("CHESTPLATE","LEGGINGS","BOOTS"):List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS");
      for(String part:parts) {
        String id="HERO_"+hero.id()+"_"+part;double weight=switch(part){case "HELMET"->1d/7;case "CHESTPLATE"->unmasked?4d/7:3d/7;case "LEGGINGS"->2d/7;default->1d/7;};
        var stats=new LinkedHashMap<String,Object>();stats.put("armor",Map.of("value",switch(part){case "HELMET"->2;case "CHESTPLATE"->unmasked?8:6;case "LEGGINGS"->4;default->2;},"decimals",1));stats.put("max-health",Map.of("value",switch(part){case "HELMET"->2;case "CHESTPLATE"->unmasked?7:5;case "LEGGINGS"->3;default->2;},"decimals",1));stats.put("attack-damage",Map.of("value",switch(part){case "HELMET"->.25;case "CHESTPLATE"->unmasked?1:.75;default->.5;},"decimals",2));
        items.put(id,Map.of("type","armor","material","minecraft:leather_"+part.toLowerCase(Locale.ROOT),"name",hero.name()+" "+part.charAt(0)+part.substring(1).toLowerCase(Locale.ROOT),"rarity","common","stats",stats));
        rules.put(id,Map.of("armor-texture","fantasyhub:heroes/"+hero.id().toLowerCase(Locale.ROOT),"item-model","fantasyhub:item/heroes/"+id.toLowerCase(Locale.ROOT),"unbreakable",true,"disable-enchanting",false));
      }
    }
    // SVFrame owns these modifiers, matching an iron axe (+8 damage, -3.1 speed).
    // Its source policy clears native item modifiers, preventing double application.
    var weapon=Map.of("HERO_THOR_STORMBREAKER",Map.of("type","tool","material","fantasyhub:stormbreaker","name","Stormbreaker","rarity","common","stats",Map.of("attack-damage",Map.of("value",8,"decimals",1),"attack-speed",Map.of("value",-3.1,"decimals",1))));
    var capes=Map.of("HERO_THOR_CAPE",Map.of("type","accessory","material","fantasyhub:thor_cape","name","Thor Cape","rarity","common","stats",Map.of()),"HERO_SORCERER_SUPREME_CAPE",Map.of("type","accessory","material","fantasyhub:cloak_of_levitation","name","Cloak of Levitation","rarity","common","stats",Map.of()));
    return Map.of("SVFrameItems/items/heroes.yml",new Yaml().dump(items).getBytes(StandardCharsets.UTF_8),"SVFrameItems/rules/heroes.yml",new Yaml().dump(rules).getBytes(StandardCharsets.UTF_8),
        "SVFrameItems/items/heroes-weapons.yml",new Yaml().dump(weapon).getBytes(StandardCharsets.UTF_8),
        "SVFrameItems/rules/heroes-weapons.yml",new Yaml().dump(Map.of("HERO_THOR_STORMBREAKER",Map.of("unbreakable",false,"disable-enchanting",false))).getBytes(StandardCharsets.UTF_8),
        "SVFrameItems/items/heroes-capes.yml",new Yaml().dump(capes).getBytes(StandardCharsets.UTF_8));
  }
}
