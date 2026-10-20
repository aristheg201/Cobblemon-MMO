package vn.svframe.fantasyhub.core.hero;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Generates ordinary native MMO class definitions, not a separate class manager. */
public final class HeroClassFiles {
  private HeroClassFiles() {}
  public static Map<String,byte[]> generate(HeroCatalog catalog) {
    var files=new TreeMap<String,byte[]>();
    for(var h:catalog.heroes().values()) {
      var c=new LinkedHashMap<String,Object>();
      String icon=switch(h.id()){case "SPIDER_MAN"->"minecraft:cobweb";case "SORCERER_SUPREME"->"minecraft:ender_eye";case "IRON_MAN"->"minecraft:iron_chestplate";case "THOR"->"minecraft:iron_axe";case "FLASH"->"minecraft:lightning_rod";default->"minecraft:bat_spawn_egg";};
      c.put("display",Map.of("name",h.name(),"lore",h.passives(),"item",Map.of("material",icon)));
      c.put("exp-curve","levels");c.put("exp-table","class_exp_table");c.put("max-level",100);
      c.put("attributes",Map.of("max-health",Map.of("base",30,"per-level",.3),"attack-damage",Map.of("base",6,"per-level",.12),"armor",Map.of("base",3,"per-level",.02),"max-mana",Map.of("base",h.resource().equals("MANA")?h.maximum():0),"max-stamina",Map.of("base",h.resource().equals("STAMINA")?h.maximum():40)));
      c.put("resource",Map.of(h.resource().toLowerCase(Locale.ROOT),Map.of("type","MAX","value",100*h.regenIdle()/h.maximum(),"off-combat",false)));
      c.put("mana",Map.of("name",h.resourceName(),"char","♦","icon","♦","color",Map.of("full","AQUA","half","DARK_AQUA","empty","GRAY")));
      var skills=new LinkedHashMap<String,Object>();var slots=new LinkedHashMap<String,Object>();
      for(var a:h.abilities()) {
        skills.put(a.id(),Map.of("level",1,"max-level",1,"unlocked-by-default",true,"upgradable",false));
        slots.put(String.valueOf(a.slot()),Map.of("name",a.name(),"formula","<ACTIVE>","unlocked-by-default",true,"hardset",a.id()));
      }
      c.put("skills",skills);c.put("skill-slots",slots);
      files.put("SVFrameMMO/classes/"+h.id().toLowerCase(Locale.ROOT)+".yml",new Yaml().dump(c).getBytes(StandardCharsets.UTF_8));
    }
    return Collections.unmodifiableMap(files);
  }
}
