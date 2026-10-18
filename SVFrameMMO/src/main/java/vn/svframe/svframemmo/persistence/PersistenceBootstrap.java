package vn.svframe.svframemmo.persistence;

import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.profess.SavedClassState;

/**
 * Verifies storage codecs and closes pooled/remote persistence resources after the normal stop-save
 * callback.
 */
public final class PersistenceBootstrap implements ModInitializer {
  @Override
  public void onInitialize() {
    verifyYamlCodec();
    ServerLifecycleEvents.SERVER_STOPPED.register(server -> SVFrameMMO.playerData().close());
  }

  public static void verifyYamlCodec() {
    SavedClassState slot =
        new SavedClassState(
            7,
            12.5d,
            3,
            4,
            1,
            2,
            3,
            18d,
            44d,
            55d,
            66d,
            Map.of("STRENGTH", 2),
            Map.of("SLASH", 3),
            Map.of(1, "SLASH"),
            Set.of("skill:slash"),
            Map.of("general", 2),
            Map.of("general_root", 1),
            Map.of("node:general_root.reward", 1));
    PlayerDataSnapshot probe =
        new PlayerDataSnapshot(
            "WARRIOR",
            9,
            42.25d,
            1,
            2,
            3,
            4,
            5,
            6,
            17d,
            30d,
            40d,
            50d,
            Map.of("STRENGTH", 3),
            Map.of("SLASH", 4),
            Map.of(2, "SLASH"),
            Set.of("slot:2", "skill:slash"),
            Map.of("class_warrior.reward", 1),
            Map.of("mining", 5),
            Map.of("mining", 9.5d),
            Map.of("general", 3),
            Map.of("general_root", 2),
            Map.of("WARRIOR", slot),
            1770000000000L,
            Map.of("fantasyhub.rider.spirit", "2"));
    YamlSnapshotCodec codec = new YamlSnapshotCodec();
    PlayerDataSnapshot restored = codec.decode(codec.encode(probe));
    var legacy = codec.decode(codec.encode(probe).split("preferences:")[0]);
    if (!legacy.preferences().isEmpty())
      throw new IllegalStateException("Legacy profiles must default to empty preferences");
    try {
      restored.preferences().put("bad", "value");
      throw new IllegalStateException("Mutable storage snapshot");
    } catch (UnsupportedOperationException expected) {
    }
    if (!probe.equals(restored))
      throw new IllegalStateException("Native YAML userdata codec failed round-trip self-check");
  }
}
