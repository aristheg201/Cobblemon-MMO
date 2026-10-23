package vn.svframe.fantasyhub.core;
import java.util.*;
/** Event queue: one UI snapshot per dispatch, coalesced and rate-limited independently of combat packets. */
final class UiSyncGate {
 record Dispatch(UUID player,String scope){}
 private record Session(String scope,long until){}
 private final Map<UUID,Session> active=new HashMap<>();
 private final LinkedHashMap<UUID,String> pending=new LinkedHashMap<>();
 private final Map<UUID,Long> last=new HashMap<>();
 void open(UUID p,String s,long t){if(!Set.of("profile","classes","shop","upgrades","pokemon").contains(s))return;active.put(p,new Session(s,t+100));pending.put(p,s);}
 void close(UUID p,String s){var a=active.get(p);if(a!=null&&a.scope.equals(s)){active.remove(p);pending.remove(p);}}
 void touch(UUID p,long t){var a=active.get(p);if(a!=null)active.put(p,new Session(a.scope,t+100));}
 String scope(UUID p,long t){var a=active.get(p);if(a==null)return null;if(t>=a.until){active.remove(p);pending.remove(p);return null;}return a.scope;}
 void dirty(UUID p){var a=active.get(p);pending.put(p,a==null?"profile":a.scope);}
 Dispatch next(long t){for(var e:List.copyOf(pending.entrySet())){var p=e.getKey();if(active.containsKey(p)&&scope(p,t)==null)continue;if(!e.getValue().equals("profile")&&!Objects.equals(scope(p,t),e.getValue())){pending.remove(p);continue;}if(t-last.getOrDefault(p,-10L)<10)continue;pending.remove(p);last.put(p,t);return new Dispatch(p,e.getValue());}return null;}
 void remove(UUID p){active.remove(p);pending.remove(p);last.remove(p);}
}
