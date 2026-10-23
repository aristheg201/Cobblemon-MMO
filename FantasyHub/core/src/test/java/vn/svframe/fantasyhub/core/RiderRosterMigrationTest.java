package vn.svframe.fantasyhub.core;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.svframelib.config.YamlLite;

class RiderRosterMigrationTest {
  @TempDir Path dir;

  @Test void oldPolicyGainsRidersAndRetainsCustomBalancePermissionsAndOtherClasses() throws Exception {
    Path file=dir.resolve("class-switch.yml");
    String original="# custom policy\nfee: 4200\npermission-prefix: myserver.class.\nadditional-native-classes: [ANTI_MAGE_KNIGHT, RYUKI]\ncustom-field: {enabled: false}\n";
    Files.writeString(file,original);
    var next=RiderRosterMigration.migrate(file,YamlLite.map(YamlLite.parse(file)));
    assertEquals(4200,next.get("fee"));
    assertEquals("myserver.class.",next.get("permission-prefix"));
    assertEquals(Map.of("enabled",false),next.get("custom-field"));
    assertEquals(List.of("ANTI_MAGE_KNIGHT","RYUKI","ZOLDA","AGITO","KNIGHT","DECADE","KABUTO"),next.get("additional-native-classes"));
    assertEquals(next,YamlLite.map(YamlLite.parse(file)));
    assertEquals(original,Files.readString(dir.resolve("class-switch.before-card-riders.yml")));
  }

  @Test void shippedVersionOneRosterGainsKabutoWithoutChangingFees() throws Exception {
    Path file=dir.resolve("class-switch.yml");String old="native-rider-roster-version: 1\nfee: 1234\nadditional-native-classes: [AGITO, ZOLDA, RYUKI, KNIGHT, DECADE, THOR]\n";Files.writeString(file,old);var next=RiderRosterMigration.migrate(file,YamlLite.map(YamlLite.parse(file)));assertTrue(((List<?>)next.get("additional-native-classes")).contains("KABUTO"));assertEquals(1234,next.get("fee"));assertEquals(old,Files.readString(dir.resolve("class-switch.before-kabuto-roster.yml")));assertSame(next,RiderRosterMigration.migrate(file,next));
  }

  @Test void laterAdministratorRemovalIsRespectedAndFileIsNotRewritten() throws Exception {
    Path file=dir.resolve("class-switch.yml");
    String edited="# intentional restriction\nnative-rider-roster-version: 1\nadditional-native-classes: [RYUKI]\n";
    Files.writeString(file,edited);
    var original=YamlLite.map(YamlLite.parse(file));
    assertSame(original,RiderRosterMigration.migrate(file,original));
    assertEquals(edited,Files.readString(file));
    assertFalse(Files.exists(dir.resolve("class-switch.before-card-riders.yml")));
  }

  @Test void absentAdditionalRosterIsCreatedWithoutChangingTheLegacySelectableList() throws Exception {
    Path file=dir.resolve("class-switch.yml");
    Files.writeString(file,"selectable-classes: [MAGE, WARRIOR]\n");
    var next=RiderRosterMigration.migrate(file,YamlLite.map(YamlLite.parse(file)));
    assertEquals(List.of("MAGE","WARRIOR"),next.get("selectable-classes"));
    assertEquals(6,((List<?>)next.get("additional-native-classes")).size());
    assertEquals(2,next.get("native-rider-roster-version"));
    assertEquals(next,RiderRosterMigration.migrate(file,next));
  }
}
