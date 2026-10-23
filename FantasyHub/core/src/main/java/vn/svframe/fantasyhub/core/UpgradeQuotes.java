package vn.svframe.fantasyhub.core;
import java.util.*;
/** Per-player, single-use confirmations tied to the exact inventory item and upgrade rules. */
final class UpgradeQuotes {
 record Quote(int slot,String item,String rules,long expires){}
 private final Map<UUID,Map<String,Quote>> players=new HashMap<>();
 String issue(UUID player,int slot,String item,String rules,long now){
  var quotes=players.computeIfAbsent(player,k->new HashMap<>());
  quotes.entrySet().removeIf(e->e.getValue().expires<=now||e.getValue().slot==slot&&(!e.getValue().item.equals(item)||!e.getValue().rules.equals(rules)));
  for(var e:quotes.entrySet())if(e.getValue().slot==slot)return e.getKey();
  String id=UUID.randomUUID().toString();quotes.put(id,new Quote(slot,item,rules,now+120_000));return id;
 }
 Quote take(UUID player,String id,long now){var map=players.get(player);if(map==null)return null;var q=map.remove(id);return q!=null&&q.expires>now?q:null;}
 void clear(UUID player){players.remove(player);}
}
