package vn.svframe.fantasyhub.core;

import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.fantasyhub.protocol.*;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

/** Opt-in real-world acceptance, never enabled in production by default. */
final class KabutoQa {
  static final Map<String, Object> RESULTS = new LinkedHashMap<>();

  static int run(ServerPlayerEntity p, String scenario) {
    var data = SVFrameMMO.playerData().get(p);
    if (scenario.equals("prepare")) {
      RESULTS.clear();
      p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
      data.changeClass(SVFrameMMO.classes().get("KABUTO"), PlayerClassChangeEvent.Reason.GUI);
      data.setLevel(100);
      p.clearStatusEffects();
      for (String id : KabutoRuntime.SKILLS) data.setSkillLevel("KABUTO_" + id, 1);
      int slot = 1;
      for (String id : KabutoRuntime.SKILLS)
        if (!id.equals("ZECTER_BOND")) PlayerSkillCatalog.bind(data, slot++, "KABUTO_" + id);
      for (var piece :
          Map.of(
                  EquipmentSlot.HEAD,
                  "HELMET",
                  EquipmentSlot.CHEST,
                  "CHESTPLATE",
                  EquipmentSlot.LEGS,
                  "LEGGINGS",
                  EquipmentSlot.FEET,
                  "BOOTS")
              .entrySet()) p.equipStack(piece.getKey(), item("KABUTO_" + piece.getValue()));
      p.equipStack(EquipmentSlot.MAINHAND, item("KABUTO_KUNAI_GUN"));
      RESULTS.put("belt_equipped_via_real_api", equipBelt(p, item("KABUTO_ZECTER_BELT")));
      SVFrameMMO.playerData().savePlayer(data);
      later(
          10,
          () -> {
            RESULTS.put("initial", KabutoRuntime.inspect(p));
            RESULTS.put(
                "equipment_definitions",
                SVFrameItems.equipment().evaluate(p).items().stream()
                    .map(v -> v.definition().id())
                    .toList());
            RESULTS.put(
                "belt_skill_damage", MMOPlayerData.setup(p).getStatMap().getStat("SKILL_DAMAGE"));
            RESULTS.put(
                "weapon_attack", p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
            RESULTS.put("masked_health", p.getMaxHealth());
            RESULTS.put(
                "masked_speed", p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
            save();
          });
      save();
      return 1;
    }
    if (scenario.equals("status")) {
      RESULTS.put("status", KabutoRuntime.inspect(p));
      save();
      return 1;
    }
    if (scenario.startsWith("armor_")) {
      String collection = scenario.substring(6).toUpperCase(Locale.ROOT);
      for (var slot :
          List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
        p.equipStack(slot, ItemStack.EMPTY);
      for (var e :
          Map.of(
                  EquipmentSlot.HEAD,
                  "HELMET",
                  EquipmentSlot.CHEST,
                  "CHESTPLATE",
                  EquipmentSlot.LEGS,
                  "LEGGINGS",
                  EquipmentSlot.FEET,
                  "BOOTS")
              .entrySet()) {
        String suffix = e.getValue();
        String id;
        if (collection.equals("GRANDMASTER_WOLVEN")) id = "WITCHER_GRANDMASTER_WOLVEN_" + suffix;
        else if (collection.equals("SKYRIM_IRON"))
          id = "SVFRAME_DRAGONBORN_ARMOR_" + (suffix.equals("HELMET") ? "HAT" : suffix);
        else id = "SVFRAME_" + collection + "_ARMOR_" + (suffix.equals("HELMET") ? "HAT" : suffix);
        if (SVFrameItems.registry().item(id) != null) p.equipStack(e.getKey(), item(id));
      }
      RESULTS.put(scenario, true);
      save();
      return 1;
    }
    Map<String, Object> r = new LinkedHashMap<>();
    RESULTS.put(scenario, r);
    p.clearStatusEffects();
    p.setHealth(p.getMaxHealth());
    data.giveMana(1000, vn.svframe.svframelib.player.resource.ResourceUpdateReason.OTHER);
    data.giveStamina(1000, vn.svframe.svframelib.player.resource.ResourceUpdateReason.OTHER);
    if (scenario.equals("charge")) {
      // Twenty successful normal native player attacks exercise the real
      // AFTER_DAMAGE listener. No direct meter assignment in this fixture.
      CowEntity target = cow(p, 2, 200);
      for (int i = 0; i < 10; i++) {
        target.timeUntilRegen = 0;
        target.damage(p.getDamageSources().playerAttack(p), 1);
      }
      r.put("state", KabutoRuntime.inspect(p));
      r.put("actual_target_damage", target.getHealth() < 200);
      target.discard();
      save();
      return 1;
    }
    String id = "KABUTO_" + scenario.toUpperCase(Locale.ROOT);
    var skill = data.getProfess().getSkill(id);
    if (skill == null) throw new IllegalArgumentException(scenario);
    var cooldown = MMOPlayerData.setup(p).getCooldownMap();
    cooldown.resetCooldown(skill.getCooldownPath());
    cooldown.resetCooldown("svframemmo_global_skill");
    p.setYaw(0);
    p.setPitch(0);
    p.networkHandler.requestTeleport(p.getX(), p.getY(), p.getZ(), 0, 0);
    CowEntity target =
        Set.of("cast_off", "rider_kick", "kunai_strike", "clock_up").contains(scenario)
            ? cow(
                p,
                scenario.equals("cast_off")
                    ? 2
                    : scenario.equals("kunai_strike")
                            && !Boolean.TRUE.equals(KabutoRuntime.inspect(p).get("gun"))
                        ? 2.5
                        : 5,
                scenario.equals("rider_kick") ? 10 : 200)
            : null;
    CowEntity slamTarget = scenario.equals("cast_off") ? cow(p, 6, 200) : null;
    if (slamTarget != null) {
      slamTarget.setAiDisabled(true);
      later(
          75,
          () -> {
            r.put("slam_damage_applied", slamTarget.getHealth() < 200);
            r.put("slam_target_health", slamTarget.getHealth());
            slamTarget.discard();
            save();
          });
    }
    double speedBefore = p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
    Vec3d position = target == null ? Vec3d.ZERO : target.getPos();
    float health = target == null ? 0 : target.getHealth();
    r.put("current_health_before", p.getHealth());
    r.put("cast_accepted", SVFrameMMO.skillRuntime().cast(data, id).isSuccessful());
    if (target != null) r.put("health_after_immediate", target.getHealth());
    r.put("state_immediate", KabutoRuntime.inspect(p));
    r.put("cooldown", cooldown.getCooldown(id));
    r.put("speed", p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
    r.put("attack_speed", p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED));
    if (scenario.equals("rider_kick"))
      r.put(
          "kill_resets_cooldown",
          target != null && !target.isAlive() && cooldown.getCooldown(id) == 0);
    if (scenario.equals("clock_up")) {
      r.put(
          "slow_rejected",
          !p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3))
              && !p.hasStatusEffect(StatusEffects.SLOWNESS));
      later(
          30,
          () -> {
            r.put("mob_ai_frozen", KabutoRuntime.frozen(target));
            r.put("mob_stayed", target.squaredDistanceTo(position) < .01);
            r.put("no_persistent_NoAI", !target.isAiDisabled());
            save();
          });
      later(
          110,
          () -> {
            r.put("expired", !KabutoRuntime.clock(p));
            r.put("mob_ai_restored", !KabutoRuntime.frozen(target) && !target.isAiDisabled());
            r.put("speed_after", p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
            target.discard();
            save();
          });
    } else if (target != null)
      later(
          75,
          () -> {
            r.put("damage_applied", target.getHealth() < health);
            r.put("health_after", target.getHealth());
            if (target.isAlive()) target.discard();
            save();
          });
    later(
        12,
        () -> {
          r.put("state_settled", KabutoRuntime.inspect(p));
          r.put("health_max", p.getMaxHealth());
          r.put("current_health_after", p.getHealth());
          save();
        });
    save();
    return 1;
  }

  private static boolean equipBelt(ServerPlayerEntity p, ItemStack belt) {
    if (!BeltEquipment.equipped(p).isEmpty()) return true;
    try {
      if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("accessories")) {
        Class<?> type = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
        Object cap = type.getMethod("get", LivingEntity.class).invoke(null, p);
        Object ref = type.getMethod("attemptToEquipAccessory", ItemStack.class).invoke(cap, belt);
        return ref != null && !BeltEquipment.equipped(p).isEmpty();
      }
      Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi"),
          type = Class.forName("dev.emi.trinkets.api.TrinketComponent");
      var component =
          (Optional<?>) api.getMethod("getTrinketComponent", LivingEntity.class).invoke(null, p);
      if (component.isEmpty()) return false;
      Map<?, ?> inventory = (Map<?, ?>) type.getMethod("getInventory").invoke(component.get());
      Object group = inventory.get("legs");
      if (!(group instanceof Map<?, ?> slots)) return false;
      Inventory slot = (Inventory) slots.get("belt");
      if (slot == null || slot.size() == 0 || !slot.getStack(0).isEmpty()) return false;
      var refType = Class.forName("dev.emi.trinkets.api.SlotReference");
      Object ref =
          refType
              .getConstructor(Class.forName("dev.emi.trinkets.api.TrinketInventory"), int.class)
              .newInstance(slot, 0);
      Object trinket =
          api.getMethod("getTrinket", net.minecraft.item.Item.class)
              .invoke(null, Protocol.zecterBelt);
      boolean allowed =
          (boolean)
              Class.forName("dev.emi.trinkets.api.Trinket")
                  .getMethod("canEquip", ItemStack.class, refType, LivingEntity.class)
                  .invoke(trinket, belt, ref, p);
      if (!allowed) return false;
      slot.setStack(0, belt);
      type.getMethod("update").invoke(component.get());
      return !BeltEquipment.equipped(p).isEmpty();
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException(error);
    }
  }

  private static ItemStack item(String id) {
    return SVFrameItems.generator().generate(id, 1);
  }

  private static CowEntity cow(ServerPlayerEntity p, double distance, float hp) {
    CowEntity c = EntityType.COW.create(p.getServerWorld());
    Vec3d at = p.getPos().add(0, 0, distance);
    c.refreshPositionAndAngles(at.x, at.y, at.z, 180, 0);
    c.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(hp);
    c.setHealth(hp);
    c.setPersistent();
    c.addCommandTag("qa_kabuto");
    p.getServerWorld().spawnEntity(c);
    return c;
  }

  private static void later(int delay, Runnable task) {
    SVFrameLibFabricMod.schedule(delay, task);
  }

  private static void save() {
    try {
      Path p = Path.of("qa/kabuto-runtime.json");
      Files.createDirectories(p.getParent());
      Files.writeString(
          p, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
