package vn.svframe.fantasyhub.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** One-time roster addition; administrator restrictions after this migration remain authoritative. */
final class HeroRosterMigration {
  static Map<String,Object> migrate(Path path,Map<String,Object> original) throws IOException {
    if(Integer.parseInt(Objects.toString(original.getOrDefault("native-hero-roster-version",0)))>=1)return original;
    var next=new LinkedHashMap<String,Object>(original);var roster=new LinkedHashSet<String>();
    if(original.get("additional-native-classes") instanceof List<?> list)list.forEach(n->roster.add(String.valueOf(n)));
    roster.addAll(List.of("SPIDER_MAN","SORCERER_SUPREME","IRON_MAN","THOR","FLASH","BATMAN"));
    next.put("additional-native-classes",new ArrayList<>(roster));next.put("native-hero-roster-version",1);
    var backup=path.resolveSibling(path.getFileName().toString().replaceFirst("\\.yml$","")+".before-heroes.yml");
    if(!Files.exists(backup))Files.copy(path,backup);
    Path temp=Files.createTempFile(path.getParent(),"hero-roster-",".tmp");
    try {Files.writeString(temp,new Yaml().dump(next));try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}}
    finally{Files.deleteIfExists(temp);}return next;
  }
}
