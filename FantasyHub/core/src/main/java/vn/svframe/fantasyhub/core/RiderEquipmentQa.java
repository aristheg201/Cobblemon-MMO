package vn.svframe.fantasyhub.core;

import java.nio.file.*;
import java.util.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.fantasyhub.protocol.Protocol;
import vn.svframe.svframeitems.SVFrameItems;

/** Opt-in native equipment review, deliberately distinct from class/skill acceptance. */
final class RiderEquipmentQa {
  static int run(ServerPlayerEntity p, String form) {
    if (form.equals("magnugiga")) {
      var stack = new ItemStack(Protocol.visualBone);
      NbtComponent.set(
          DataComponentTypes.CUSTOM_DATA,
          stack,
          n -> n.putString("SVFRAME_ITEM_MODEL", "fantasyhub:item/riders/magnugiga"));
      var display =
          new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, p.getServerWorld());
      var tag = new net.minecraft.nbt.NbtCompound();
      tag.put("item", stack.encode(p.getRegistryManager()));
      tag.putString("item_display", "fixed");
      var transform = new net.minecraft.nbt.NbtCompound();
      var scale = new net.minecraft.nbt.NbtList();
      for (int i = 0; i < 3; i++) scale.add(net.minecraft.nbt.NbtFloat.of(1.6f));
      transform.put("scale", scale);
      var translation = new net.minecraft.nbt.NbtList();
      for (int i = 0; i < 3; i++) translation.add(net.minecraft.nbt.NbtFloat.of(0));
      transform.put("translation", translation);
      for (String key : List.of("left_rotation", "right_rotation")) {
        var quaternion = new net.minecraft.nbt.NbtList();
        for (int i = 0; i < 4; i++) quaternion.add(net.minecraft.nbt.NbtFloat.of(i == 3 ? 1 : 0));
        transform.put(key, quaternion);
      }
      tag.put("transformation", transform);
      display.readNbt(tag);
      display.addCommandTag("rider_model_qa");
      display.setPosition(p.getPos().add(0, 0, 3));
      display.setYaw(p.getYaw());
      p.getServerWorld().spawnEntity(display);
      return 1;
    }
    if (!Set.of(
            "zolda",
            "agito_ground",
            "agito_storm",
            "agito_flame",
            "agito_trinity",
            "agito_shining",
            "ryuki", "knight", "decade", "human")
        .contains(form)) return 0;
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("scenario", "native_equipment_render");
    report.put("form", form);
    report.put("classGameplayVerified", false);
    for (var row :
        Map.of(
                EquipmentSlot.HEAD,
                "helmet",
                EquipmentSlot.CHEST,
                "chestplate",
                EquipmentSlot.LEGS,
                "leggings",
                EquipmentSlot.FEET,
                "boots")
            .entrySet()) {
      String id = form + '_' + row.getValue();
      var definition = SVFrameItems.registry().item(id);
      if (!form.equals("human") && definition == null)
        throw new IllegalStateException("Missing authored Rider item " + id);
      var stack = form.equals("human") ? ItemStack.EMPTY : SVFrameItems.generator().generate(id, 1);
      p.equipStack(row.getKey(), stack);
      report.put(row.getKey().name(), definition == null ? "" : definition.id());
    }
    String weapon =
        switch (form) {
          case "ryuki" -> "ryuki_drag_saber";
          case "knight" -> "knight_dark_visor";
          case "decade" -> "decade_ride_booker_sword";
          case "zolda" -> "zolda_magna_visor";
          case "agito_storm" -> "agito_storm_halberd";
          case "agito_flame" -> "agito_flame_saber";
          case "agito_shining" -> "agito_shining_caliber";
          default -> "";
        };
    p.equipStack(
        EquipmentSlot.MAINHAND,
        weapon.isEmpty() ? ItemStack.EMPTY : SVFrameItems.generator().generate(weapon, 1));
    p.equipStack(
        EquipmentSlot.OFFHAND,
        form.equals("agito_shining")
            ? SVFrameItems.generator().generate(weapon, 1)
            : ItemStack.EMPTY);
    report.put("weapon", weapon);
    report.put("serverHealth", p.getHealth());
    report.put("serverMaxHealth", p.getMaxHealth());
    try {
      Path path = Path.of("qa/rider-models");
      Files.createDirectories(path);
      Files.writeString(
          path.resolve(form + ".json"),
          new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
    return 1;
  }
}
