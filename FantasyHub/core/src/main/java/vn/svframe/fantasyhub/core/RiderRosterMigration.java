package vn.svframe.fantasyhub.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** One-time upgrade of the existing class policy, without changing prices or permissions. */
final class RiderRosterMigration {
  private static final String REVISION = "native-rider-roster-version";

  static Map<String, Object> migrate(Path path, Map<String, Object> original) throws IOException {
    int revision=Integer.parseInt(Objects.toString(original.getOrDefault(REVISION,0)));
    if(revision>=2)return original;
    // A restricted v1 roster reflects an administrator removal; do not widen it.
    if(revision==1&&(!(original.get("additional-native-classes") instanceof List<?> values)||!values.containsAll(List.of("ZOLDA","AGITO","RYUKI","KNIGHT","DECADE"))))return original;
    var next = new LinkedHashMap<String, Object>(original);
    var classes = new LinkedHashSet<String>();
    if (original.get("additional-native-classes") instanceof List<?> values)
      for (var value : values) classes.add(String.valueOf(value));
    classes.addAll(List.of("ZOLDA", "AGITO", "RYUKI", "KNIGHT", "DECADE", "KABUTO"));
    next.put("additional-native-classes", new ArrayList<>(classes));
    next.put(REVISION, 2);
    var backup = path.resolveSibling(revision==1?"class-switch.before-kabuto-roster.yml":"class-switch.before-card-riders.yml");
    if (!Files.exists(backup)) Files.copy(path, backup);
    var temporary = Files.createTempFile(path.getParent(), "class-policy-", ".tmp");
    try {
      Files.writeString(temporary, new Yaml().dump(next));
      try {
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
    return next;
  }
}
