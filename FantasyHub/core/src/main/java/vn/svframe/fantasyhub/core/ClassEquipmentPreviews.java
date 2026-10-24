package vn.svframe.fantasyhub.core;

import java.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.ItemGenerator;

/** Registry revision cache, filled in bounded main-thread slices when the class GUI requests it. */
final class ClassEquipmentPreviews {
  private record Job(String classId, String itemId) {}

  private static long revision = -1;
  private static int cursor;
  private static List<Job> jobs = List.of();
  private static final Map<String, List<String>> building = new LinkedHashMap<>();
  private static Map<String, List<String>> cache = Map.of();

  static boolean prepare(ServerPlayerEntity player, long budgetNanos) {
    if (revision != SVFrameItems.registry().revision()) {
      building.clear();
      cache = Map.of();
      cursor = 0;
      var pending = new ArrayList<Job>();
      for (String classId :
          List.of(
              "DRAGONBORN", "HUNTER", "KABUTO", "RYUKI", "KNIGHT", "DECADE", "ZOLDA", "AGITO")) {
        String prefix =
            switch (classId) {
              case "DRAGONBORN" -> "SVFRAME_DRAGONBORN_ARMOR";
              case "HUNTER" -> "SVFRAME_HUNTER";
              case "AGITO" -> "AGITO_GROUND";
              default -> classId;
            };
        building.put(classId, new ArrayList<>());
        for (String slot :
            List.of(
                classId.equals("DRAGONBORN") ? "HAT" : "HELMET",
                "CHESTPLATE",
                "LEGGINGS",
                "BOOTS")) {
          String id = prefix + "_" + slot;
          if (SVFrameItems.registry().item(id) != null) pending.add(new Job(classId, id));
        }
      }
      jobs = List.copyOf(pending);
      revision = SVFrameItems.registry().revision();
    }
    long start = System.nanoTime();
    for (int n = 0;
        cursor < jobs.size() && n < 2 && (n == 0 || System.nanoTime() - start < budgetNanos);
        n++) {
      var job = jobs.get(cursor++);
      var stack =
          SVFrameItems.generator()
              .generate(job.itemId(), new ItemGenerator.GenerationContext(1, 0));
      building.get(job.classId()).add(stack.encode(player.getRegistryManager()).toString());
    }
    if (cursor < jobs.size()) return false;
    if (cache.isEmpty()) {
      var ready = new LinkedHashMap<String, List<String>>();
      building.forEach((id, items) -> ready.put(id, List.copyOf(items)));
      cache = Map.copyOf(ready);
    }
    return true;
  }

  static Map<String, List<String>> snapshots(ServerPlayerEntity player) {
    return cache;
  }

  static void clear() {
    revision = -1;
    jobs = List.of();
    building.clear();
    cache = Map.of();
    cursor = 0;
  }
}
