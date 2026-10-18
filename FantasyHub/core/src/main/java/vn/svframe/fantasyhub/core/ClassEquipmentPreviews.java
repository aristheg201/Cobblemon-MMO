package vn.svframe.fantasyhub.core;

import java.util.*;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.ItemGenerator;

/** Authoritative item presentation snapshots; no client item generation or gameplay mutations. */
final class ClassEquipmentPreviews {
  private static long revision = -1;
  private static Map<String, List<String>> cache = Map.of();

  static Map<String, List<String>> snapshots(ServerPlayerEntity player) {
    if (revision != SVFrameItems.registry().revision()) {
      List<String> items = new ArrayList<>();
      for (String suffix : List.of("HAT", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
        String id = "SVFRAME_DRAGONBORN_ARMOR_" + suffix;
        if (SVFrameItems.registry().item(id) == null) continue;
        var stack =
            SVFrameItems.generator().generate(id, new ItemGenerator.GenerationContext(1, 0));
        items.add(stack.encode(player.getRegistryManager()).toString());
      }
      List<String> hunter = new ArrayList<>();
      for (String suffix : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
        String id = "SVFRAME_HUNTER_" + suffix;
        if (SVFrameItems.registry().item(id) != null)
          hunter.add(
              SVFrameItems.generator()
                  .generate(id, new ItemGenerator.GenerationContext(1, 0))
                  .encode(player.getRegistryManager())
                  .toString());
      }
      List<String> kabuto = new ArrayList<>();
      for (String suffix : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
        String id = "KABUTO_" + suffix;
        if (SVFrameItems.registry().item(id) != null)
          kabuto.add(
              SVFrameItems.generator()
                  .generate(id, new ItemGenerator.GenerationContext(1, 0))
                  .encode(player.getRegistryManager())
                  .toString());
      }
      var updated = new LinkedHashMap<String, List<String>>();
      updated.put("DRAGONBORN", List.copyOf(items));
      updated.put("HUNTER", List.copyOf(hunter));
      updated.put("KABUTO", List.copyOf(kabuto));
      for (String rider : List.of("RYUKI", "KNIGHT", "DECADE", "ZOLDA", "AGITO")) {
        String prefix = rider.equals("AGITO") ? "AGITO_GROUND" : rider;
        var armor = new ArrayList<String>();
        for (String suffix : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"))
          if (SVFrameItems.registry().item(prefix + "_" + suffix) != null)
            armor.add(
                SVFrameItems.generator()
                    .generate(prefix + "_" + suffix, new ItemGenerator.GenerationContext(1, 0))
                    .encode(player.getRegistryManager())
                    .toString());
        updated.put(rider, List.copyOf(armor));
      }
      cache = Map.copyOf(updated);
      revision = SVFrameItems.registry().revision();
    }
    return cache;
  }
}
