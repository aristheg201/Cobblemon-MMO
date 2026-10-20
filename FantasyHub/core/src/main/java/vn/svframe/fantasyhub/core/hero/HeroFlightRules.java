package vn.svframe.fantasyhub.core.hero;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-owned equipment conditions; personal skin capes are presentation only. */
public record HeroFlightRules(int doubleTapTicks,int landingGraceTicks,Map<String,Rule> rules){
  public record Rule(boolean enabled,String capeItem,Map<String,String> requiredArmor,float speed,double upkeepPerSecond){}
  public HeroFlightRules{rules=Map.copyOf(rules);}
  public Rule rule(String hero){return rules.get(hero);}
  public boolean allows(String hero,String functionalBackItem,Map<String,String> armor){
    var r=rule(hero);return r!=null&&r.enabled&&r.capeItem.equals(functionalBackItem)&&r.requiredArmor.entrySet().stream().allMatch(e->e.getValue().equalsIgnoreCase(armor.getOrDefault(e.getKey(),"")));
  }
  public static HeroFlightRules defaults() throws IOException{
    try(var in=HeroFlightRules.class.getResourceAsStream("/default/hero-flight.json")){
      if(in==null)throw new IOException("Missing hero-flight.json");return parse(JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject());
    }
  }
  public static HeroFlightRules parse(JsonObject json){
    int taps=json.get("doubleTapTicks").getAsInt(),grace=json.get("landingGraceTicks").getAsInt();
    if(taps<4||taps>12||grace<0||grace>400)throw new IllegalArgumentException("Invalid flight input/landing window");
    var rules=new LinkedHashMap<String,Rule>();
    for(var e:json.getAsJsonObject("rules").entrySet()){
      if(!Set.of("THOR","SORCERER_SUPREME").contains(e.getKey()))throw new IllegalArgumentException("Unsupported cape flight class "+e.getKey());
      var o=e.getValue().getAsJsonObject();String cape=o.get("capeItem").getAsString();float speed=o.get("speed").getAsFloat();double upkeep=o.get("upkeepPerSecond").getAsDouble();
      if(!cape.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||!Float.isFinite(speed)||speed<=0||speed>.15||!Double.isFinite(upkeep)||upkeep<0||upkeep>100)throw new IllegalArgumentException("Invalid flight cape/speed/upkeep");
      var armor=new LinkedHashMap<String,String>();for(var a:o.getAsJsonObject("requiredArmor").entrySet()){
        if(!Set.of("HEAD","CHEST","LEGS","FEET").contains(a.getKey())||a.getValue().getAsString().isBlank())throw new IllegalArgumentException("Invalid required armor slot");armor.put(a.getKey(),a.getValue().getAsString());
      }
      rules.put(e.getKey(),new Rule(o.get("enabled").getAsBoolean(),cape,Map.copyOf(armor),speed,upkeep));
    }
    return new HeroFlightRules(taps,grace,rules);
  }
}
