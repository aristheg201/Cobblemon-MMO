package vn.svframe.svframemmo.cobblemon.gameplay;
import java.util.*;
/** First matching explicit rule wins, including over the Alpha default. All specified criteria are conjunctive. */
public final class TemperamentRules {
 public enum Kind { FRIENDLY,NEUTRAL,HOSTILE }
 public Kind defaultTemperament=Kind.NEUTRAL,alphaDefault=Kind.HOSTILE;
 public int neutralTimeoutTicks=600;public List<Rule> overrides=new ArrayList<>();
 public static final class Rule {public String species="",form="",nature="",dimension="";public int minLevel=1,maxLevel=100;public Boolean alpha;public double[] region;public Kind temperament=Kind.NEUTRAL;}
 public TemperamentRules(){for(String species:List.of("chansey","blissey","audino")){var r=new Rule();r.species=species;r.temperament=Kind.FRIENDLY;overrides.add(r);}for(String species:List.of("houndoom","gyarados","gengar","darkrai")){var r=new Rule();r.species=species;r.temperament=Kind.HOSTILE;overrides.add(r);}}
 public Kind resolve(String species,String form,int level,String nature,String dimension,boolean alpha,double x,double y,double z){
  for(var r:overrides)if(match(r.species,species)&&match(r.form,form)&&match(r.nature,nature)&&match(r.dimension,dimension)&&level>=r.minLevel&&level<=r.maxLevel&&(r.alpha==null||r.alpha==alpha)&&(r.region==null||x>=r.region[0]&&y>=r.region[1]&&z>=r.region[2]&&x<=r.region[3]&&y<=r.region[4]&&z<=r.region[5]))return r.temperament;
  return alpha?alphaDefault:defaultTemperament;
 }
 private static boolean match(String expected,String value){return expected==null||expected.isBlank()||expected.equalsIgnoreCase(value)||expected.equalsIgnoreCase(value.replace("cobblemon:",""));}
 public void validate(){if(defaultTemperament==null||alphaDefault==null||neutralTimeoutTicks<20||neutralTimeoutTicks>72000||overrides==null)throw new IllegalArgumentException("Invalid temperament policy");for(var r:overrides)if(r==null||r.temperament==null||r.minLevel<1||r.maxLevel<r.minLevel||r.maxLevel>10000||r.region!=null&&(r.region.length!=6||Arrays.stream(r.region).anyMatch(v->!Double.isFinite(v))||r.region[0]>r.region[3]||r.region[1]>r.region[4]||r.region[2]>r.region[5]))throw new IllegalArgumentException("Invalid temperament override");}
}
