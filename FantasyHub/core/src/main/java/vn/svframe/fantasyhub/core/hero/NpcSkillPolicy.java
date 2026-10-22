package vn.svframe.fantasyhub.core.hero;
import java.util.*;
/** Config rotation names are resolved against the same owned class catalog as player skills. */
public final class NpcSkillPolicy {
 private static final Set<String> OPTIONAL=Set.of("GOD_OF_THUNDER","DETECTIVE_VISION","SUPER_SPEED","SLOW_MOTION","ASTRAL_PROJECTION","IMAGES_OF_IKONN","HOUSE_PARTY","WEB_SHIELD","STORMBREAKER_THROW");
 private NpcSkillPolicy(){}
 public static long cycleTicks(HeroCatalog.Ability ability,int windup,int recovery,double cooldownMultiplier){
  long last=ability.phases().stream().mapToLong(p->(long)((Math.max(0,p.start())+Math.max(0,p.hits()-1)*p.interval())*20)).max().orElse(0);
  long action=Math.max(ability.key().equals("STORMBREAKER_THROW")?28:16,last+1);
  return windup+action+Math.max(20,(long)(recovery*cooldownMultiplier));
 }
 public static List<HeroCatalog.Ability> resolve(HeroCatalog catalog,String classId,List<String> ids){
  if(classId==null||classId.isBlank())throw new IllegalArgumentException("boss.classId is required");
  var hero=catalog.hero(classId.toUpperCase(Locale.ROOT));
  if(ids==null||ids.size()>32)throw new IllegalArgumentException("boss.skillIds must be a list of at most 32 skills");
  if(ids.isEmpty())return hero.abilities().stream().filter(a->!OPTIONAL.contains(a.key())).toList();
  var out=new ArrayList<HeroCatalog.Ability>();
  for(var id:ids){if(id==null||id.isBlank())throw new IllegalArgumentException("Empty boss skill ID");String full=id.toUpperCase(Locale.ROOT);if(!full.startsWith(hero.id()+"_"))full=hero.id()+"_"+full;
   var ability=catalog.ability(full);if(!hero.abilities().contains(ability))throw new IllegalArgumentException("Boss class "+classId+" does not own "+id);out.add(ability);
  }
  return List.copyOf(out);
 }
}
