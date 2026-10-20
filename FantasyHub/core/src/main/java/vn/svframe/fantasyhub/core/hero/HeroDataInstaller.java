package vn.svframe.fantasyhub.core.hero;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Crash-safe data installation. Only unedited defaults owned by this installer are upgraded. */
public final class HeroDataInstaller {
  private HeroDataInstaller() {}
  public static boolean install(Path directory,Map<String,byte[]> definitions) throws IOException {
    Path root=directory.toAbsolutePath().normalize();var files=new TreeMap<String,byte[]>();
    for(var e:definitions.entrySet()) {
      Path relative=Path.of(e.getKey());Path target=root.resolve(relative).normalize();
      if(relative.isAbsolute()||!target.startsWith(root)||relative.getNameCount()<2||!Set.of("SVFrameMMO","SVFrameItems","FantasyHub").contains(relative.getName(0).toString())||e.getValue().length>4_000_000)
        throw new IllegalArgumentException("Unsafe superhero data path "+e.getKey());
      files.put(e.getKey(),e.getValue().clone());
    }
    Path state=root.resolve("FantasyHub/hero-data-installation.json");
    JsonObject old=Files.exists(state)?JsonParser.parseString(Files.readString(state)).getAsJsonObject():new JsonObject();
    JsonObject next=old.deepCopy();boolean changed=false;
    for(var e:files.entrySet()) {
      Path target=root.resolve(e.getKey());String wanted=hash(e.getValue());
      if(Files.exists(target)) {
        String current=hash(Files.readAllBytes(target));
        if(current.equals(wanted)){next.addProperty(e.getKey(),wanted);continue;}
        if(!old.has(e.getKey())||!current.equals(old.get(e.getKey()).getAsString()))continue;
        Files.copy(target,target.resolveSibling(target.getFileName()+".before-heroes"),StandardCopyOption.REPLACE_EXISTING);
      }
      atomic(target,e.getValue());next.addProperty(e.getKey(),wanted);changed=true;
    }
    if(!next.equals(old)||!Files.exists(state))atomic(state,new GsonBuilder().setPrettyPrinting().create().toJson(next).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return changed;
  }
  private static String hash(byte[] b) {
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
    catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
  }
  private static void atomic(Path target,byte[] data) throws IOException {
    Files.createDirectories(target.getParent());Path temp=Files.createTempFile(target.getParent(),"hero-data-",".tmp");
    try{Files.write(temp,data);try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}}
    finally{Files.deleteIfExists(temp);}
  }
}
