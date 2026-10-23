package vn.svframe.worldboss;
import java.util.*;
/** Persisted encounter state. Damage is supplied only after server health has changed. */
public final class Encounter {
 public enum State { SETUP,FIGHT,WON,LOST,SETTLEMENT,ABORTED,CLOSED }
 public String id,boss,arena,actor;public UUID owner;public String ticketId,returnDimension;public double returnX,returnY,returnZ;public boolean personal,ticketConsumed,returnPending,returnComplete,lootRolled,lootDelivered;public UUID lootRecipient;public java.util.List<String> loot=new java.util.ArrayList<>();public long disconnectedAt;public long setupAt,fightAt,deadline,finishAt;public double maximumHealth;public State state,outcome;public boolean forcedChunk;
 public Map<UUID,Contribution> contributions=new LinkedHashMap<>();
 public static final class Contribution { public double damage;public long seconds;public long firstDamageAt=-1; }
 public Encounter(String id,String boss,String arena,long start,double hp,int setup,int fight){this.id=id;this.boss=boss;this.arena=arena;setupAt=start;fightAt=start+setup;deadline=fightAt+fight;maximumHealth=hp;state=State.SETUP;}
 public boolean fight(long now){if(state!=State.SETUP||now<fightAt)return false;long duration=deadline-fightAt;fightAt=now;deadline=now+duration;state=State.FIGHT;return true;}
 public void damage(UUID player,double amount){damage(player,amount,fightAt);}
 public void damage(UUID player,double amount,long now){if(state!=State.FIGHT||!Double.isFinite(amount)||amount<=0)return;var c=contributions.computeIfAbsent(player,k->new Contribution());if(c.firstDamageAt<0)c.firstDamageAt=Math.max(fightAt,now);c.damage+=Math.min(amount,maximumHealth);}
 public void presence(UUID player,long seconds){var c=contributions.get(player);if(state==State.FIGHT&&seconds>0&&c!=null&&c.damage>0)c.seconds+=seconds;}
 public boolean eligible(UUID player,double fraction,long seconds){var c=contributions.get(player);if(c==null||c.damage<=0||c.damage<maximumHealth*fraction)return false;long available=Math.max(0,finishAt-Math.max(fightAt,c.firstDamageAt));long required=outcome==State.WON&&finishAt-fightAt<seconds?Math.min(seconds,available):seconds;long credited=c.seconds;if(outcome==State.WON||outcome==State.LOST)credited=Math.min(credited,available);return credited>=required;}
 public boolean expired(long now){return state==State.FIGHT&&now>=deadline;}
 public boolean finish(boolean win){return finish(win,deadline);}
 public boolean finish(boolean win,long now){if(state!=State.FIGHT)return false;finishAt=Math.max(fightAt,now);state=win?State.WON:State.LOST;outcome=state;return true;}
 public void abort(){if(state==State.SETUP||state==State.FIGHT)state=State.ABORTED;}
 public Map<UUID,Integer> rewards(int win,int loss,double fraction,long seconds){var out=new LinkedHashMap<UUID,Integer>();if(outcome!=State.WON&&outcome!=State.LOST)return out;for(var id:contributions.keySet())if(eligible(id,fraction,seconds))out.put(id,outcome==State.WON?win:loss);return out;}
}
