package vn.svframe.fantasyhub.core;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.*;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.fantasyhub.protocol.*;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

/** Native, opt-in integration probes. No assertion here labels the whole class complete. */
final class RiderQa {
  static int run(ServerPlayerEntity p, String scenario) {
    var data = SVFrameMMO.playerData().get(p);
    var report = new LinkedHashMap<String, Object>();
    report.put("scenario", scenario);
    if (scenario.startsWith("prepare_")) {
      String type = scenario.substring(8).toUpperCase(Locale.ROOT);
      if (!RiderRuntime.SKILLS.containsKey(type)) return 0;
      SVFrameMMO.skillBar().leave(data);
      var profess = SVFrameMMO.classes().get(type);
      if (profess == null) throw new IllegalStateException("Rider class not loaded: " + type);
      if (!data.getClassId().equals(type)
          && !data.changeClass(profess, PlayerClassChangeEvent.Reason.GUI))
        throw new IllegalStateException("Class change cancelled");
      p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
      data.setLevel(100);
      data.setMana(
          data.getMaxResource(
              vn.svframe.svframemmo.api.player.profess.resource.PlayerResource.MANA),
          vn.svframe.svframelib.player.resource.ResourceUpdateReason.OTHER);
      data.setStamina(
          data.getMaxResource(
              vn.svframe.svframemmo.api.player.profess.resource.PlayerResource.STAMINA),
          vn.svframe.svframelib.player.resource.ResourceUpdateReason.OTHER);
      p.setHealth(p.getMaxHealth());
      var cooldowns = data.getMMOPlayerData().getCooldownMap();
      cooldowns.clearAllCooldowns();
      int slot = 1;
      for (String ability : RiderRuntime.SKILLS.get(type)) {
        String id = type + "_" + ability;
        data.setSkillLevel(id, 1);
        if (java.util.Set.of("RYUKI", "KNIGHT", "DECADE").contains(type)
            && ability.equals("HENSHIN")) continue;
        PlayerSkillCatalog.bind(data, slot++, id);
      }
      var belt = SVFrameItems.generator().generate(type.toLowerCase(Locale.ROOT) + "_belt", 1);
      report.put("equippedViaRealApi", equipBelt(p, belt, report));
      for (var equipment :
          List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
        p.equipStack(equipment, ItemStack.EMPTY);
      p.equipStack(
          EquipmentSlot.MAINHAND,
          type.equals("ZOLDA")
              ? SVFrameItems.generator().generate("zolda_magna_visor", 1)
              : ItemStack.EMPTY);
      p.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
      SVFrameMMO.playerData().savePlayer(data);
      report.put("class", data.getClassId());
      report.put("bound", data.getSkillBindings());
    } else if (scenario.startsWith("cast_")) {
      String id = scenario.substring(5).toUpperCase(Locale.ROOT);
      report.put("manaBefore", data.getMana());
      report.put("staminaBefore", data.getStamina());
      report.put("inventoryBefore", inventory(p));
      report.put("before", RiderRuntime.inspect(p));
      var result = SVFrameMMO.skillRuntime().cast(data, id);
      report.put("castResult", result.isSuccessful());
      report.put("manaAfter", data.getMana());
      report.put("staminaAfter", data.getStamina());
      report.put("inventoryAfter", inventory(p));
      report.put(
          "cooldownAfter",
          data.getMMOPlayerData().getCooldownMap().getCooldown(id.toLowerCase(Locale.ROOT)));
    } else if (!scenario.equals("status")) return 0;
    report.put("immediate", RiderRuntime.inspect(p));
    save(p, report, false);
    vn.svframe.svframelib.fabric.SVFrameLibFabricMod.schedule(
        70,
        () -> {
          report.put("settled", RiderRuntime.inspect(p));
          report.put("maxHealth", p.getMaxHealth());
          report.put("health", p.getHealth());
          save(p, report, true);
        });
    return 1;
  }

  private static String inventory(ServerPlayerEntity p) {
    return p.getInventory().writeNbt(new net.minecraft.nbt.NbtList()).toString();
  }

  private static void save(ServerPlayerEntity p, Map<String, Object> report, boolean settled) {
    try {
      Path folder = Path.of("qa/rider-gameplay");
      Files.createDirectories(folder);
      var copy = new LinkedHashMap<>(report);
      copy.put("settledReport", settled);
      Files.writeString(
          folder.resolve(p.getUuid() + ".json"),
          new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(copy) + "\n");
    } catch (java.io.IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private static boolean equipBelt(
      ServerPlayerEntity p, ItemStack belt, Map<String, Object> report) {
    try {
      if (FabricLoader.getInstance().isModLoaded("accessories")) {
        var type = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
        var cap = type.getMethod("get", LivingEntity.class).invoke(null, p);
        if (!BeltEquipment.equippedAny(p).isEmpty())
          throw new IllegalStateException("Remove the previous QA belt first");
        var ref = type.getMethod("attemptToEquipAccessory", ItemStack.class).invoke(cap, belt);
        return ref != null && !BeltEquipment.equipped(p, belt.getItem()).isEmpty();
      }
      Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi"),
          type = Class.forName("dev.emi.trinkets.api.TrinketComponent");
      var component =
          (Optional<?>) api.getMethod("getTrinketComponent", LivingEntity.class).invoke(null, p);
      report.put("beltComponentPresent", component.isPresent());
      if (component.isEmpty()) return false;
      var inventory = (Map<?, ?>) type.getMethod("getInventory").invoke(component.get());
      report.put("beltInventoryGroups", inventory.keySet());
      if (!(inventory.get("legs") instanceof Map<?, ?> slots)) return false;
      var slot = (Inventory) slots.get("belt");
      report.put("beltSlotSize", slot == null ? -1 : slot.size());
      if (slot == null || slot.size() == 0) return false;
      slot.setStack(0, ItemStack.EMPTY);
      // Trinkets update() rebuilds every inventory; retaining slot here writes
      // into an orphan inventory. No slot layout change is needed for setStack.
      var refType = Class.forName("dev.emi.trinkets.api.SlotReference");
      var ref =
          refType
              .getConstructor(Class.forName("dev.emi.trinkets.api.TrinketInventory"), int.class)
              .newInstance(slot, 0);
      var trinket =
          api.getMethod("getTrinket", net.minecraft.item.Item.class).invoke(null, belt.getItem());
      boolean allowed =
          (boolean)
              Class.forName("dev.emi.trinkets.api.Trinket")
                  .getMethod("canEquip", ItemStack.class, refType, LivingEntity.class)
                  .invoke(trinket, belt, ref, p);
      report.put("beltCanEquip", allowed);
      report.put("beltSlotId", refType.getMethod("getId").invoke(ref));
      report.put(
          "beltCandidateId",
          net.minecraft.registry.Registries.ITEM.getId(belt.getItem()).toString());
      report.put("beltAdapterClass", trinket.getClass().getName());
      var existing = BeltEquipment.equippedAny(p);
      report.put(
          "beltExistingAfterClear",
          existing.isEmpty()
              ? ""
              : net.minecraft.registry.Registries.ITEM.getId(existing.getItem()).toString());
      var field = BeltEquipment.class.getDeclaredField("BELTS");
      field.setAccessible(true);
      var predicates = (Map<?, ?>) field.get(null);
      var predicate = (java.util.function.Predicate<LivingEntity>) predicates.get(belt.getItem());
      report.put("beltClassPredicate", predicate != null && predicate.test(p));
      if (!allowed) return false;
      slot.setStack(0, belt);
      type.getMethod("update").invoke(component.get());
      return !BeltEquipment.equipped(p, belt.getItem()).isEmpty();
    } catch (ReflectiveOperationException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
