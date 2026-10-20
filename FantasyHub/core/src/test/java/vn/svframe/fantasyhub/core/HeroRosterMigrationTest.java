package vn.svframe.fantasyhub.core;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.svframelib.config.YamlLite;
class HeroRosterMigrationTest {
  @TempDir Path root;
  @Test void addsSixClassesOnceWithoutChangingPricesPermissionsOrExistingRoster() throws Exception {
    var p=root.resolve("classes.yml");String original="fee: 4200\npermission-prefix: custom.class.\nadditional-native-classes: [RYUKI]\n";Files.writeString(p,original);
    var result=HeroRosterMigration.migrate(p,YamlLite.map(YamlLite.parse(p)));
    assertEquals(4200,result.get("fee"));assertEquals("custom.class.",result.get("permission-prefix"));
    assertEquals(List.of("RYUKI","SPIDER_MAN","SORCERER_SUPREME","IRON_MAN","THOR","FLASH","BATMAN"),result.get("additional-native-classes"));
    Files.writeString(p,"native-hero-roster-version: 1\nadditional-native-classes: [THOR]\n");
    var restricted=YamlLite.map(YamlLite.parse(p));assertSame(restricted,HeroRosterMigration.migrate(p,restricted));
    assertEquals(original,Files.readString(root.resolve("classes.before-heroes.yml")));
  }
}
