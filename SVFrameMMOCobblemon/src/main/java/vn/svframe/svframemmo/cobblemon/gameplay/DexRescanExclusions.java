package vn.svframe.svframemmo.cobblemon.gameplay;
import com.google.gson.*;import java.nio.file.*;import java.nio.channels.FileChannel;import java.nio.ByteBuffer;import java.nio.charset.StandardCharsets;import java.io.IOException;import java.util.*;
/** Only suppresses startup rescan of the exact owned Pokemon present when a dex record was wiped. */
public final class DexRescanExclusions {
 private final Path file;private final Map<UUID,Set<UUID>> excluded=new HashMap<>();
 public DexRescanExclusions(Path file)throws IOException{this.file=file;if(Files.exists(file)){
  var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();for(var row:root.entrySet()){var ids=new HashSet<UUID>();for(var raw:row.getValue().getAsJsonArray())ids.add(UUID.fromString(raw.getAsString()));excluded.put(UUID.fromString(row.getKey()),ids);}
 }}
 public boolean keeps(UUID player,UUID pokemon){return !excluded.getOrDefault(player,Set.of()).contains(pokemon);}
 public void exclude(UUID player,Collection<UUID> pokemon)throws IOException{
  var next=new HashSet<>(excluded.getOrDefault(player,Set.of()));next.addAll(pokemon);var root=new JsonObject();
  var snapshot=new HashMap<>(excluded);snapshot.put(player,next);snapshot.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(row->{var list=new JsonArray();row.getValue().stream().sorted().forEach(id->list.add(id.toString()));root.add(row.getKey().toString(),list);});
  Files.createDirectories(file.toAbsolutePath().getParent());Path temporary=Files.createTempFile(file.toAbsolutePath().getParent(),"dex-exclusions-",".tmp");
  try{try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)){var bytes=ByteBuffer.wrap(root.toString().getBytes(StandardCharsets.UTF_8));while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}excluded.put(player,next);}finally{Files.deleteIfExists(temporary);}
 }
}
