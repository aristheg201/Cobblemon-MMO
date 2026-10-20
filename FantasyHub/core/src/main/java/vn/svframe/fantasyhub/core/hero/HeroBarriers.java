package vn.svframe.fantasyhub.core.hero;

import java.util.*;

/** Temporary absorption effects, keyed by their real owner; repeated casts refresh instead of multiplying. */
final class HeroBarriers {
  private record Shield(UUID owner,UUID target,double left,long until) {}
  private static final Map<String,Shield> SHIELDS=new LinkedHashMap<>();
  static void add(UUID owner,UUID target,String ability,double maximumHp,double fraction,long until) {
    String key=owner+":"+target+":"+ability;SHIELDS.remove(key);
    double existing=SHIELDS.values().stream().filter(s->s.target.equals(target)).mapToDouble(Shield::left).sum();
    double amount=Math.max(0,Math.min(maximumHp*fraction,maximumHp*.35-existing));
    if(amount>0)SHIELDS.put(key,new Shield(owner,target,amount,until));
  }
  static double absorb(UUID target,double incoming) {
    for(var entry:SHIELDS.entrySet()) {
      var s=entry.getValue();if(!s.target.equals(target)||incoming<=0)continue;
      double spent=Math.min(s.left,incoming);incoming-=spent;entry.setValue(new Shield(s.owner,s.target,s.left-spent,s.until));
    }
    return incoming;
  }
  static double amount(UUID target){return SHIELDS.values().stream().filter(s->s.target.equals(target)).mapToDouble(Shield::left).sum();}
  static void destroy(UUID target){SHIELDS.values().removeIf(s->s.target.equals(target));}
  static void release(UUID owner){SHIELDS.values().removeIf(s->s.owner.equals(owner));}
  static void tick(long tick){SHIELDS.values().removeIf(s->s.until<=tick||s.left<=0);}
  static void clear(){SHIELDS.clear();}
}
