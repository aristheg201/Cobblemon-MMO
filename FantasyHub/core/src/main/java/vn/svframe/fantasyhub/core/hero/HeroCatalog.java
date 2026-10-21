package vn.svframe.fantasyhub.core.hero;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Immutable validated class/ability definitions. Unknown/incomplete input never installs partially. */
public final class HeroCatalog {
  public record Phase(String kind,double scale,int hits,double start,double interval) {}
  public record Ability(String id,String key,String name,String nameVi,int slot,double cooldown,
      double cost,double range,double radius,double duration,double recast,double upkeep,
      double castTime,boolean ultimate,List<Phase> phases,Map<String,Double> effects) {
    public Ability {phases=List.copyOf(phases);effects=Map.copyOf(effects);}
    public double totalCoefficient(){return phases.stream().filter(p->!p.kind.equals("MAX_HEALTH")).mapToDouble(p->p.scale*p.hits).sum();}
    public double effect(String key){return effects.getOrDefault(key,0d);}
  }
  public record Hero(String id,String name,String nameVi,String resource,String resourceName,
      String resourceNameVi,double maximum,String suit,List<String> passives,double regenIdle,
      double regenCombat,List<Ability> abilities,Map<String,Double> passiveValues) {
    public Hero {passives=List.copyOf(passives);abilities=List.copyOf(abilities);passiveValues=Map.copyOf(passiveValues);}
    public double passive(String key){var n=passiveValues.get(key);if(n==null)throw new IllegalArgumentException("Unknown passive value "+id+"/"+key);return n;}
  }
  private final Map<String,Hero> heroes;
  private final Map<String,Ability> abilities;
  private final JsonObject balance;
  private HeroCatalog(Map<String,Hero> heroes,Map<String,Ability> abilities,JsonObject balance){this.heroes=Map.copyOf(heroes);this.abilities=Map.copyOf(abilities);this.balance=balance.deepCopy();}
  public Map<String,Hero> heroes(){return heroes;}
  public Map<String,Ability> abilities(){return abilities;}
  public Hero hero(String id){var h=heroes.get(id);if(h==null)throw new IllegalArgumentException("Unknown hero "+id);return h;}
  public Ability ability(String id){var a=abilities.get(id);if(a==null)throw new IllegalArgumentException("Unknown hero skill "+id);return a;}
  public double setting(String key){return number(balance,key,0,10000);}
  public static HeroCatalog defaults() throws IOException {return parse(defaultJson());}
  public static JsonObject defaultJson() throws IOException {
    try(var in=HeroCatalog.class.getResourceAsStream("/default/heroes.json")) {
      if(in==null)throw new IOException("Missing superhero catalog");
      return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
    }
  }
  public static HeroCatalog parse(JsonObject root) {
    try {
      if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unsupported hero schema");
      var heroes=new LinkedHashMap<String,Hero>();var skills=new LinkedHashMap<String,Ability>();
      for(var raw:root.getAsJsonArray("heroes")) {
        var h=raw.getAsJsonObject();String id=text(h,"id");var list=new ArrayList<Ability>();
        for(var entry:h.getAsJsonArray("abilities")) {
          var a=entry.getAsJsonObject();var phases=new ArrayList<Phase>();
          for(var phase:a.getAsJsonArray("phases")) {
            var p=phase.getAsJsonObject();String kind=text(p,"kind");
            if(!Set.of("PHYSICAL","MAGIC","TRUE_PHYSICAL","TRUE_MAGIC","MAX_HEALTH").contains(kind))throw new IllegalArgumentException("Unknown hero damage kind");
            int hits=integer(p,"hits",1,32);
            phases.add(new Phase(kind,number(p,"scale",0,20),hits,number(p,"start",-1,120),number(p,"interval",0,120)));
          }
          var effects=new LinkedHashMap<String,Double>();
          for(var e:a.getAsJsonObject("effects").entrySet())effects.put(e.getKey(),number(a.getAsJsonObject("effects"),e.getKey(),0,200));
          var ability=new Ability(text(a,"id"),text(a,"key"),text(a,"name"),text(a,"name_vi"),integer(a,"slot",1,8),number(a,"cooldown",.05,600),number(a,"cost",0,500),number(a,"range",0,40),number(a,"radius",0,20),number(a,"duration",0,120),number(a,"recast",0,10),number(a,"upkeep",0,100),number(a,"cast_time",0,10),a.get("ultimate").getAsBoolean(),phases,effects);
          if(!ability.id.equals(id+"_"+ability.key)||skills.putIfAbsent(ability.id,ability)!=null)throw new IllegalArgumentException("Duplicate/mismatched hero ability ID");
          list.add(ability);
        }
        if(list.size()!=(id.equals("THOR")?8:7))throw new IllegalArgumentException("Incomplete active skills for "+id);
        for(int i=0;i<list.size();i++)if(list.get(i).slot!=i+1)throw new IllegalArgumentException("Invalid hero skill slot order");
        if(list.stream().filter(Ability::ultimate).count()!=1)throw new IllegalArgumentException("Exactly one ultimate required");
        String resource=text(h,"resource");if(!Set.of("MANA","STAMINA").contains(resource))throw new IllegalArgumentException("Unknown native resource");
        var passives=new ArrayList<String>();for(var p:h.getAsJsonArray("passives"))passives.add(p.getAsString());
        if(passives.size()!=2)throw new IllegalArgumentException("Missing hero passives");
        var passiveValues=new LinkedHashMap<String,Double>();
        for(var e:h.getAsJsonObject("passive_values").entrySet())passiveValues.put(e.getKey(),number(h.getAsJsonObject("passive_values"),e.getKey(),0,1000));
        var hero=new Hero(id,text(h,"name"),text(h,"name_vi"),resource,text(h,"resource_name"),text(h,"resource_name_vi"),number(h,"maximum",1,500),text(h,"suit"),passives,number(h,"regen_idle",0,100),number(h,"regen_combat",0,100),list,passiveValues);
        if(heroes.putIfAbsent(id,hero)!=null)throw new IllegalArgumentException("Duplicate hero ID");
      }
      if(!heroes.keySet().equals(Set.of("SPIDER_MAN","SORCERER_SUPREME","IRON_MAN","THOR","FLASH","BATMAN")))throw new IllegalArgumentException("Incomplete superhero class roster");
      var balance=root.getAsJsonObject("balance");
      for(String key:List.of("pvp_multiplier","regular_cast_hp_cap","ultimate_cast_hp_cap","pvp_slow_cap","cooldown_reduction_cap"))number(balance,key,0,1);
      for(String key:List.of("ultimate_attack_budget","pvp_control_cap_ticks","control_window_ticks","pvp_silence_cap_ticks","max_targets","ultimate_max_targets","max_displacement"))number(balance,key,0,10000);
      return new HeroCatalog(heroes,skills,balance);
    } catch(RuntimeException malformed) {throw new IllegalArgumentException("Invalid superhero configuration: "+malformed.getMessage(),malformed);}
  }
  private static String text(JsonObject o,String key){var s=o.get(key).getAsString();if(s.isBlank())throw new IllegalArgumentException("Missing "+key);return s;}
  private static double number(JsonObject o,String key,double min,double max){double d=o.get(key).getAsDouble();if(!Double.isFinite(d)||d<min||d>max)throw new IllegalArgumentException("Invalid "+key);return d;}
  private static int integer(JsonObject o,String key,int min,int max){double d=number(o,key,min,max);if(d!=(int)d)throw new IllegalArgumentException("Nonintegral "+key);return (int)d;}
}
