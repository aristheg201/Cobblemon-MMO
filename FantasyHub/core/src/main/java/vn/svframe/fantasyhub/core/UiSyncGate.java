package vn.svframe.fantasyhub.core;
import java.util.*;
/** Only leases remain here; the domain scheduler owns all coalescing, fairness and backpressure. */
final class UiSyncGate {
 private record Session(String scope,long until){}private final Map<UUID,Session> active=new HashMap<>();
 boolean open(UUID p,String s,long t){if(!Set.of("profile","classes","shop","upgrades","pokemon").contains(s))return false;active.put(p,new Session(s,t+100));return true;}
 boolean close(UUID p,String s){var a=active.get(p);if(a==null||!a.scope.equals(s))return false;active.remove(p);return true;}
 void touch(UUID p,long t){var a=active.get(p);if(a!=null)active.put(p,new Session(a.scope,t+100));}
 String scope(UUID p,long t){var a=active.get(p);if(a==null)return null;if(t>=a.until){active.remove(p);return null;}return a.scope;}
 void remove(UUID p){active.remove(p);}void clear(){active.clear();}
}
