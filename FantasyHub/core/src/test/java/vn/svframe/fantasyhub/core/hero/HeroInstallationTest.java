package vn.svframe.fantasyhub.core.hero;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.svframelib.config.YamlLite;
class HeroInstallationTest {
  @TempDir Path root;
  @Test void generatedClassesUseNativeResourcesAndExactlySevenHardsetSkills() throws Exception {
    var catalog=HeroCatalog.defaults();var files=HeroClassFiles.generate(catalog);
    assertEquals(6,files.size());
    for(var h:catalog.heroes().values()) {
      var raw=files.get("SVFrameMMO/classes/"+h.id().toLowerCase(Locale.ROOT)+".yml");assertNotNull(raw);
      var config=YamlLite.map(YamlLite.parse(new String(raw,StandardCharsets.UTF_8)));
      var slots=YamlLite.map(config.get("skill-slots"));assertEquals(7,slots.size());
      for(var a:h.abilities())assertEquals(a.id(),YamlLite.map(slots.get(String.valueOf(a.slot()))).get("hardset"));
      assertFalse(YamlLite.map(config.get("skills")).keySet().stream().anyMatch(s->s.contains("HENSHIN")));
      var resource=YamlLite.map(config.get("resource"));assertTrue(resource.containsKey(h.resource().toLowerCase(Locale.ROOT)));
    }
  }
  @Test void administratorChangesSurviveReinstallationWhileOwnedDefaultsCanUpgrade() throws Exception {
    var path="SVFrameMMO/classes/iron_man.yml";var one=Map.of(path,"first".getBytes(StandardCharsets.UTF_8));
    assertTrue(HeroDataInstaller.install(root,one));assertFalse(HeroDataInstaller.install(root,one));
    assertTrue(HeroDataInstaller.install(root,Map.of(path,"second".getBytes(StandardCharsets.UTF_8))));
    Files.writeString(root.resolve(path),"administrator edits");
    assertFalse(HeroDataInstaller.install(root,one));assertEquals("administrator edits",Files.readString(root.resolve(path)));
  }
  @Test void invalidPathsRejectTheWholeInstallBeforeAnyConfigurationIsWritten() {
    var files=new LinkedHashMap<String,byte[]>();files.put("SVFrameMMO/classes/a.yml",new byte[]{1});files.put("../escape",new byte[]{2});
    assertThrows(IllegalArgumentException.class,()->HeroDataInstaller.install(root,files));
    assertFalse(Files.exists(root.resolve("SVFrameMMO/classes/a.yml")));
  }
  @Test void passiveNumbersAreExplicitRatherThanHiddenInHandlers() throws Exception {
    var c=HeroCatalog.defaults();assertEquals(.2,c.hero("SPIDER_MAN").passive("evasion"));
    assertEquals(5,c.hero("SPIDER_MAN").passive("instinct_max"));
    assertEquals(1.5,c.hero("IRON_MAN").passive("flight_upkeep"));
    assertEquals(4,c.hero("FLASH").passive("health_regen_multiplier"));
    assertEquals(.15,c.hero("BATMAN").passive("parry"));
  }
}
