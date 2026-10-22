package vn.svframe.worldboss;
import com.google.gson.*;import java.nio.file.*;import java.nio.channels.FileChannel;import java.nio.ByteBuffer;import java.nio.charset.StandardCharsets;import java.io.*;import java.util.*;
/** Atomic, fsynced intent journal. Native player claim markers protect delivery after crash. */
public final class BossLedger {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();private transient Path path;
 public Set<String> occurrences=new LinkedHashSet<>();public Map<String,Encounter> encounters=new LinkedHashMap<>();public Map<String,Reward> rewards=new LinkedHashMap<>();public int rotation;
 public static final class Reward {public String encounter;public UUID player;public int amount;public boolean delivered;Reward(String e,UUID p,int a){encounter=e;player=p;amount=a;}}
 public static BossLedger load(Path path)throws IOException{BossLedger ledger=Files.exists(path)?JSON.fromJson(Files.readString(path),BossLedger.class):new BossLedger();if(ledger==null)throw new IOException("Empty boss ledger");ledger.path=path;return ledger;}
 public void recover(){for(var e:encounters.values())if(e.state==Encounter.State.SETUP||e.state==Encounter.State.FIGHT)e.abort();}
 public void settle(Encounter e,int win,int loss,double fraction,long seconds){if(e.state!=Encounter.State.WON&&e.state!=Encounter.State.LOST)return;queue(e.id,e.rewards(win,loss,fraction,seconds));e.state=Encounter.State.SETTLEMENT;closeIfDelivered(e.id);}
 private void closeIfDelivered(String encounter){var e=encounters.get(encounter);if(e!=null&&e.state==Encounter.State.SETTLEMENT&&rewards.values().stream().filter(r->r.encounter.equals(encounter)).allMatch(r->r.delivered))e.state=Encounter.State.CLOSED;}
 public void queue(String encounter,Map<UUID,Integer> amounts){amounts.forEach((player,amount)->rewards.putIfAbsent(encounter+":"+player,new Reward(encounter,player,amount)));}
 public List<Reward> pending(UUID player){return rewards.values().stream().filter(r->!r.delivered&&r.player.equals(player)).toList();}
 public void delivered(String encounter,UUID player){var r=rewards.get(encounter+":"+player);if(r!=null)r.delivered=true;closeIfDelivered(encounter);}
 public void save()throws IOException{atomic(path,JSON.toJson(this));}
 public static void atomic(Path path,String value)throws IOException{Files.createDirectories(path.getParent());var tmp=path.resolveSibling(path.getFileName()+".tmp");try(var channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){var bytes=ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}try{Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException error){Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);}try(var channel=FileChannel.open(path.getParent(),StandardOpenOption.READ)){channel.force(true);}catch(IOException ignored){/* Some filesystems do not support directory fsync. */}}
}
