package vn.svframe.skript.fabric;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import vn.svframe.skript.lang.Values.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Encode on the server thread; coalesce snapshots and atomically write on one dedicated I/O worker. */
final class VariableStore implements AutoCloseable {
    private final Path file;private final MinecraftServer server;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"SVFrameSkript-variables");t.setDaemon(true);return t;});
    private final AtomicReference<String> pending=new AtomicReference<>();private final AtomicBoolean working=new AtomicBoolean();
    private final AtomicReference<IOException> failure=new AtomicReference<>();
    VariableStore(Path file,MinecraftServer server){this.file=file;this.server=server;}
    static void validate(Object value){
        if(value instanceof Number number){if(!Double.isFinite(number.doubleValue()))throw new IllegalArgumentException("Persistent numbers must be finite");return;}
        if(value instanceof Span span){if(!Double.isFinite(span.milliseconds()))throw new IllegalArgumentException("Persistent duration must be finite");return;}
        if(value instanceof Location loc){if(!Double.isFinite(loc.x())||!Double.isFinite(loc.y())||!Double.isFinite(loc.z())||!Float.isFinite(loc.yaw())||!Float.isFinite(loc.pitch()))throw new IllegalArgumentException("Invalid persistent location");return;}
        if(value instanceof NativeApiBridge.Vector v){if(!Double.isFinite(v.x())||!Double.isFinite(v.y())||!Double.isFinite(v.z()))throw new IllegalArgumentException("Invalid persistent vector");return;}
        if(value instanceof Boolean||value instanceof String||value instanceof UUID||value instanceof EntityRef||value instanceof WorldRef||value instanceof GameTime||value instanceof vn.svframe.skript.lang.Values.Date||value instanceof FabricPlatform.ItemValue)return;
        throw new IllegalArgumentException("Variable type cannot persist: "+value.getClass().getSimpleName());
    }
    Map<String,Object> load() throws IOException {
        if(!Files.exists(file))return Map.of();try{JsonObject root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();if(root.get("format").getAsInt()!=1)throw new IOException("Unsupported variable store format");Map<String,Object> out=new LinkedHashMap<>();root.getAsJsonObject("variables").entrySet().forEach(e->out.put(e.getKey(),decode(e.getValue().getAsJsonObject())));return out;}catch(RuntimeException e){throw new IOException("Cannot decode persistent Skript variables",e);}
    }
    void save(Map<String,Object> variables){
        if(!server.isOnThread())throw new IllegalStateException("Variable snapshot capture must run on server thread");
        JsonObject root=new JsonObject(),values=new JsonObject();root.addProperty("format",1);variables.forEach((key,value)->values.add(key,encode(value)));root.add("variables",values);pending.set(root.toString());schedule();
    }
    private void schedule(){if(!working.compareAndSet(false,true))return;writer.submit(()->{
        try{String value;while((value=pending.getAndSet(null))!=null){try{Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);var buffer=java.nio.ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}}catch(IOException e){failure.set(e);SVFrameSkript.LOG.error("Persistent Skript variable write failed",e);}}}
        finally{working.set(false);if(pending.get()!=null&&!writer.isShutdown())schedule();}
    });}
    private JsonObject encode(Object value){
        JsonObject out=new JsonObject();
        if(value instanceof Boolean v){out.addProperty("type","boolean");out.addProperty("value",v);}
        else if(value instanceof Number v){out.addProperty("type","number");out.addProperty("value",v);}
        else if(value instanceof String v){out.addProperty("type","text");out.addProperty("value",v);}
        else if(value instanceof UUID v){out.addProperty("type","uuid");out.addProperty("value",v.toString());}
        else if(value instanceof EntityRef v){out.addProperty("type","entity");out.addProperty("uuid",v.id().toString());out.addProperty("player",v.player());}
        else if(value instanceof WorldRef v){out.addProperty("type","world");out.addProperty("value",v.id());}
        else if(value instanceof GameTime v){out.addProperty("type","game-time");out.addProperty("value",v.ticks());}
        else if(value instanceof vn.svframe.skript.lang.Values.Date v){out.addProperty("type","date");out.addProperty("value",v.milliseconds());}
        else if(value instanceof Span v){out.addProperty("type","duration");out.addProperty("value",v.milliseconds());}
        else if(value instanceof Location v){out.addProperty("type","location");out.addProperty("world",v.world().id());out.addProperty("x",v.x());out.addProperty("y",v.y());out.addProperty("z",v.z());out.addProperty("yaw",v.yaw());out.addProperty("pitch",v.pitch());}
        else if(value instanceof NativeApiBridge.Vector v){out.addProperty("type","vector");out.addProperty("x",v.x());out.addProperty("y",v.y());out.addProperty("z",v.z());}
        else if(value instanceof FabricPlatform.ItemValue v){out.addProperty("type","item");out.add("value",ItemStack.CODEC.encodeStart(server.getRegistryManager().getOps(JsonOps.INSTANCE),v.stack).getOrThrow());}
        else throw new IllegalArgumentException("Cannot persist variable type "+value.getClass().getName());
        return out;
    }
    private Object decode(JsonObject json){return switch(json.get("type").getAsString()){
        case "boolean"->json.get("value").getAsBoolean();case "number"->json.get("value").getAsDouble();case "text"->json.get("value").getAsString();case "uuid"->UUID.fromString(json.get("value").getAsString());
        case "entity"->new EntityRef(UUID.fromString(json.get("uuid").getAsString()),json.get("player").getAsBoolean());case "world"->new WorldRef(json.get("value").getAsString());
        case "game-time"->new GameTime(json.get("value").getAsInt());
        case "date"->new vn.svframe.skript.lang.Values.Date(json.get("value").getAsLong());case "duration"->new Span(json.get("value").getAsDouble());
        case "location"->new Location(new WorldRef(json.get("world").getAsString()),json.get("x").getAsDouble(),json.get("y").getAsDouble(),json.get("z").getAsDouble(),json.get("yaw").getAsFloat(),json.get("pitch").getAsFloat());
        case "vector"->new NativeApiBridge.Vector(json.get("x").getAsDouble(),json.get("y").getAsDouble(),json.get("z").getAsDouble());
        case "item"->new FabricPlatform.ItemValue(ItemStack.CODEC.parse(server.getRegistryManager().getOps(JsonOps.INSTANCE),json.get("value")).getOrThrow());
        default->throw new IllegalArgumentException("Unknown stored variable type "+json.get("type"));};}
    @Override public void close() throws IOException {writer.shutdown();try{if(!writer.awaitTermination(30,TimeUnit.SECONDS))throw new IOException("Persistent variable writes did not finish");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}if(failure.get()!=null)throw failure.get();}
}
