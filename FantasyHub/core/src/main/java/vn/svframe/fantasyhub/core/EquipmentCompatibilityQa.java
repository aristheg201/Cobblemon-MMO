package vn.svframe.fantasyhub.core;

import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.*;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.*;
import net.minecraft.item.*;
import net.minecraft.network.packet.s2c.play.ExperienceBarUpdateS2CPacket;
import net.minecraft.registry.*;
import net.minecraft.screen.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vn.svframe.fantasyhub.protocol.*;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.runtime.*;

/** Invoked only by the opt-in, operator-only runtime QA command. Uses real handlers and mod APIs. */
final class EquipmentCompatibilityQa {
  static int run(ServerPlayerEntity player, String scenario) {
    try {
      switch (scenario) {
        case "enchant" -> enchant(player);
        case "inspect" -> inspect(player);
        case "filament_equip" -> {
          Identifier id=Identifier.of("svframe:half_moon_hat");
          if(!Registries.ITEM.containsId(id))throw new IllegalStateException("Actual uploaded Filament hat is not registered");
          player.equipStack(EquipmentSlot.HEAD,new ItemStack(Registries.ITEM.get(id)));
          inspect(player);
        }
        case "passives" -> passives(player);
        case "quests" -> ThirdPartyCompatibilityQa.quests(player);
        case "mining" -> ThirdPartyCompatibilityQa.mining(player);
        default -> throw new IllegalArgumentException(scenario);
      }
      return 1;
    } catch(Exception failure) {
      save("failed-"+scenario,Map.of("verdict","FAIL","failure",failure.toString()));
      throw new IllegalStateException("Compatibility QA failed: "+scenario,failure);
    }
  }

  private static void enchant(ServerPlayerEntity player) {
    int level=player.experienceLevel,total=player.totalExperience;float progress=player.experienceProgress;
    List<Map<String,Object>> results=new ArrayList<>();
    var registry=player.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
    try {
      for(var definition:SVFrameItems.registry().items().stream().sorted(Comparator.comparing(v->v.id())).toList()) {
        ItemStack stack=SVFrameItems.generator().generate(definition.id(),1);
        if(EnchantmentPolicy.carrier(stack)==null)continue;
        Map<String,Object> row=new LinkedHashMap<>();row.put("item",definition.id());
        check(row,"thermal_tuning",ThermalTuningRuntime.hasThermalTuning(stack));
        check(row,"table_accepts_existing_thermal",stack.isEnchantable());
        player.experienceLevel=100;player.totalExperience=100000;player.experienceProgress=0;
        var table=new EnchantmentScreenHandler(0,player.getInventory(),ScreenHandlerContext.create(player.getServerWorld(),player.getBlockPos()));
        table.getSlot(0).setStack(stack.copy());table.getSlot(1).setStack(new ItemStack(Items.LAPIS_LAZULI,3));
        table.onContentChanged(table.getSlot(0).inventory);
        boolean enchanted=false;
        for(int button=0;button<3&&!enchanted;button++)enchanted=table.onButtonClick(player,button);
        ItemStack output=table.getSlot(0).getStack();
        check(row,"native_table_executes",enchanted);
        check(row,"other_enchantment_added",output.get(DataComponentTypes.ENCHANTMENTS).getEnchantments().size()>1);
        check(row,"thermal_preserved",ThermalTuningRuntime.hasThermalTuning(output));
        check(row,"lapis_and_xp_consumed",table.getSlot(1).getStack().getCount()<3&&player.experienceLevel<100);
        results.add(row);
      }
      ItemStack hat=SVFrameItems.generator().generate("KABUTO_HELMET",1);
      var book=new ItemStack(Items.ENCHANTED_BOOK);book.addEnchantment(registry.entryOf(Enchantments.PROTECTION),3);
      var anvil=new AnvilScreenHandler(0,player.getInventory(),ScreenHandlerContext.create(player.getServerWorld(),player.getBlockPos()));
      anvil.getSlot(0).setStack(hat);anvil.getSlot(1).setStack(book);anvil.updateResult();
      Map<String,Object> extra=new LinkedHashMap<>();
      check(extra,"native_anvil_book_output",!anvil.getSlot(2).getStack().isEmpty());
      check(extra,"native_anvil_applies_protection",EnchantmentHelper.getLevel(registry.entryOf(Enchantments.PROTECTION),anvil.getSlot(2).getStack())==3);
      check(extra,"anvil_keeps_thermal",ThermalTuningRuntime.hasThermalTuning(anvil.getSlot(2).getStack()));
      check(extra,"wrong_category_rejected",!registry.entryOf(Enchantments.SHARPNESS).value().isAcceptableItem(hat));
      check(extra,"vanilla_items_unchanged",!new ItemStack(Items.PAPER).isEnchantable());
      var grindstone=new GrindstoneScreenHandler(0,player.getInventory(),ScreenHandlerContext.create(player.getServerWorld(),player.getBlockPos()));
      var intrinsic=SVFrameItems.generator().generate("KABUTO_HELMET",1);
      grindstone.getSlot(0).setStack(intrinsic);grindstone.onContentChanged(grindstone.getSlot(0).inventory);
      check(extra,"grindstone_keeps_intrinsic_thermal",ThermalTuningRuntime.hasThermalTuning(grindstone.getSlot(2).getStack()));
      var xp=(vn.svframe.svframeitems.mixin.GrindstoneExperienceAccess)grindstone.getSlot(2);
      check(extra,"automatic_thermal_cannot_farm_grindstone_xp",xp.svframe$experience(intrinsic)==0);
      intrinsic.addEnchantment(registry.entryOf(Enchantments.PROTECTION),3);
      grindstone.onContentChanged(grindstone.getSlot(0).inventory);
      check(extra,"legitimate_grindstone_xp_preserved",xp.svframe$experience(intrinsic)>0);
      check(extra,"grindstone_removes_other_enchantments",EnchantmentHelper.getLevel(registry.entryOf(Enchantments.PROTECTION),grindstone.getSlot(2).getStack())==0);
      check(extra,"intrinsic_thermal_keeps_original_weapon_colors",!SVFrameItems.generator().generate("KABUTO_KUNAI_GUN",1).getOrDefault(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE,true));
      save("enchant",Map.of("verdict","PASS","equipment_count",results.size(),"items",results,"regressions",extra));
    } finally {
      player.experienceLevel=level;player.totalExperience=total;player.experienceProgress=progress;
      player.networkHandler.sendPacket(new ExperienceBarUpdateS2CPacket(progress,total,level));
    }
  }

  private static void inspect(ServerPlayerEntity player) throws Exception {
    Map<String,Object> report=new LinkedHashMap<>();report.put("kabuto",KabutoRuntime.inspect(player));
    report.put("maxHealth",player.getMaxHealth());report.put("health",player.getHealth());
    report.put("regeneration",player.hasStatusEffect(StatusEffects.REGENERATION));
    report.put("nightVision",player.hasStatusEffect(StatusEffects.NIGHT_VISION));
    Map<String,String> stacks=new LinkedHashMap<>();
    for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND))
      stacks.put(slot.getName(),player.getEquippedStack(slot).encodeAllowEmpty(player.getRegistryManager()).toString());
    stacks.put("belt",BeltEquipment.equipped(player).encodeAllowEmpty(player.getRegistryManager()).toString());report.put("inventory",stacks);
    var holders=net.minecraft.entity.LivingEntity.class.getDeclaredField("filamentCosmeticHolder");holders.setAccessible(true);
    report.put("filament_holders",((Map<?,?>)holders.get(player)).keySet());
    Class<?> temperature=Class.forName("toughasnails.api.temperature.TemperatureLevel");
    var modifier=Class.forName("toughasnails.temperature.TemperatureHelperImpl").getDeclaredMethod("armorModifier",net.minecraft.entity.player.PlayerEntity.class,temperature);modifier.setAccessible(true);
    report.put("thermal_hot",modifier.invoke(null,player,temperature.getField("HOT").get(null)).toString());
    report.put("thermal_icy",modifier.invoke(null,player,temperature.getField("ICY").get(null)).toString());
    save("inspect",report);
  }

  private static void passives(ServerPlayerEntity player) {
    Map<String,Object> report=new LinkedHashMap<>();
    check(report,"real_belt_equipped",!BeltEquipment.equipped(player).isEmpty());
    check(report,"belt_has_native_thermal",ThermalTuningRuntime.hasThermalTuning(BeltEquipment.equipped(player)));
    check(report,"night_vision_active",player.hasStatusEffect(StatusEffects.NIGHT_VISION));
    check(report,"regeneration_active",player.hasStatusEffect(StatusEffects.REGENERATION));
    float start=Math.max(1,player.getMaxHealth()-10);player.setHealth(start);
    vn.svframe.svframelib.fabric.SVFrameLibFabricMod.schedule(110,()->{
      try {
        check(report,"native_regeneration_heals",player.getHealth()>start);
        check(report,"healing_has_no_hurt_timer",player.hurtTime==0);
        // A separate lease proves pre-existing potion effects survive lease cleanup.
        var other=net.minecraft.entity.EntityType.COW.create(player.getServerWorld());
        var effect=new StatusEffectInstance(StatusEffects.NIGHT_VISION,1200,0);
        other.addStatusEffect(effect);var before=other.getStatusEffect(StatusEffects.NIGHT_VISION);
        ZecterPassives lease=new ZecterPassives();lease.tick(other,true,true);lease.clear(other);
        check(report,"existing_night_vision_preserved",other.getStatusEffect(StatusEffects.NIGHT_VISION)==before);
        check(report,"own_regeneration_removed",!other.hasStatusEffect(StatusEffects.REGENERATION));
        other.removeStatusEffect(StatusEffects.NIGHT_VISION);lease.tick(other,true,true);
        check(report,"own_night_vision_no_flashing",other.getStatusEffect(StatusEffects.NIGHT_VISION).isInfinite());
        lease.clear(other);check(report,"own_night_vision_removed",!other.hasStatusEffect(StatusEffects.NIGHT_VISION));
        lease.tick(other,true,true);
        other.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,80,1));
        lease.clear(other);
        var stronger=other.getStatusEffect(StatusEffects.REGENERATION);
        check(report,"stronger_potion_preserved",stronger!=null&&stronger.getAmplifier()==1);
        check(report,"hidden_belt_lease_cannot_reappear",((vn.svframe.fantasyhub.core.mixin.StatusEffectLeaseAccess)stronger).fantasyhub$hidden()==null);
        report.put("verdict","PASS");
      } catch(Exception failure){report.put("verdict","FAIL");report.put("failure",failure.toString());}
      save("passives",report);
    });
  }

  static void check(Map<String,Object> report,String key,boolean result) {report.put(key,result);if(!result)throw new IllegalStateException(key);}
  static void save(String name,Object report) {
    try {Path root=Path.of("qa/equipment-mining-0.1.9");Files.createDirectories(root);Files.writeString(root.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}
    catch(java.io.IOException failure){throw new IllegalStateException(failure);}
  }
}
