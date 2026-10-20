package vn.svframe.fantasyhub.core.hero;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

/** Install data before class registries attach players. No Forge code or mod archive is installed. */
public final class HeroBootstrap {
  private HeroBootstrap() {}
  public static void install() throws IOException {
    var root=FabricLoader.getInstance().getConfigDir();
    byte[] defaults;
    try(var in=HeroBootstrap.class.getResourceAsStream("/default/heroes.json")) {
      if(in==null)throw new IOException("Missing superhero definitions");defaults=in.readAllBytes();
    }
    HeroDataInstaller.install(root,Map.of("FantasyHub/heroes.json",defaults));
    try(var in=HeroBootstrap.class.getResourceAsStream("/default/hero-flight.json")){
      if(in==null)throw new IOException("Missing hero flight rules");HeroDataInstaller.install(root,Map.of("FantasyHub/hero-flight.json",in.readAllBytes()));
    }
    var c=HeroCatalog.parse(JsonParser.parseString(Files.readString(root.resolve("FantasyHub/heroes.json"))).getAsJsonObject());
    HeroDataInstaller.install(root,HeroClassFiles.generate(c));
    HeroDataInstaller.install(root,HeroEquipmentFiles.generate(c));
  }
}
