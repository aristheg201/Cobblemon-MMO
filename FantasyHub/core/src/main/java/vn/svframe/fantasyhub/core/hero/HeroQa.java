package vn.svframe.fantasyhub.core.hero;

import static net.minecraft.server.command.CommandManager.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;

/** Opt-in real world integration probes; reports observations, never fabricated screenshots. */
public final class HeroQa {
  private static final boolean ENABLED="1".equals(System.getenv("SVFRAME_RUNTIME_QA"));
  private static final Map<UUID,List<Map<String,Object>>> HITS=new HashMap<>();
  static void hit(HeroRuntime.Cast c,LivingEntity target,double applied){
    if(!ENABLED)return;var hits=HITS.computeIfAbsent(c.owner.player.getUuid(),u->new ArrayList<>());
    if(hits.size()<512)hits.add(Map.of("ability",c.ability.id(),"cast",c.id,"target",target.getUuidAsString(),"damage",applied,"tick",HeroRuntime.tick));
  }
  static void install(){if(!ENABLED)return;
    CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->dispatcher.register(literal("heroqa").requires(s->s.hasPermissionLevel(2)).then(argument("player",EntityArgumentType.player()).then(argument("action",StringArgumentType.word()).executes(c->run(EntityArgumentType.getPlayer(c,"player"),StringArgumentType.getString(c,"action"),"")).then(argument("value",StringArgumentType.greedyString()).executes(c->run(EntityArgumentType.getPlayer(c,"player"),StringArgumentType.getString(c,"action"),StringArgumentType.getString(c,"value"))))))));
  }
  private static int run(ServerPlayerEntity p,String action,String value){
    var data=SVFrameMMO.playerData().get(p);var report=new LinkedHashMap<String,Object>();report.put("action",action);report.put("value",value);report.put("tick",HeroRuntime.tick);
    switch(action) {
      case "prepare" -> {
        HeroRuntime.resetForQa(p);clearTestAccessories(p);p.clearStatusEffects();
        String id=value.toUpperCase(Locale.ROOT);if(!HeroRuntime.catalog().heroes().containsKey(id))throw new IllegalArgumentException("Unknown QA class");
        SVFrameMMO.skillBar().leave(data);var profess=SVFrameMMO.classes().get(id);if(profess==null)throw new IllegalStateException("Class did not load: "+id);
        if(!data.getClassId().equals(id)&&!data.changeClass(profess,PlayerClassChangeEvent.Reason.GUI))throw new IllegalStateException("Class change cancelled");
        p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);data.setLevel(50);
        for(var slot:net.minecraft.entity.EquipmentSlot.values())p.equipStack(slot,ItemStack.EMPTY);
        for(var a:HeroRuntime.catalog().hero(id).abilities()){data.setSkillLevel(a.id(),1);PlayerSkillCatalog.bind(data,a.slot(),a.id());}
        data.setMana(data.getMaxResource(PlayerResource.MANA),ResourceUpdateReason.COMMAND);data.setStamina(data.getMaxResource(PlayerResource.STAMINA),ResourceUpdateReason.COMMAND);
        data.getMMOPlayerData().getCooldownMap().clearAllCooldowns();p.setHealth(p.getMaxHealth());report.put("bindings",data.getSkillBindings());
      }
      case "fixture" -> {
        for(var t:p.getServerWorld().getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(40),e->e.getCommandTags().contains("hero_qa_target")))t.discard();
        var at=p.getPos().add(HeroWorld.forward(p).multiply(3));
        var t=EntityType.COW.create(p.getServerWorld());if(t==null)throw new IllegalStateException("No cow");
        t.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);t.setAiDisabled(true);t.addCommandTag("hero_qa_target");t.refreshPositionAndAngles(at.x,at.y,at.z,0,0);p.getServerWorld().spawnEntity(t);report.put("target",t.getUuidAsString());
      }
      case "equip" -> {
        for(var slot:List.of(net.minecraft.entity.EquipmentSlot.HEAD,net.minecraft.entity.EquipmentSlot.CHEST,net.minecraft.entity.EquipmentSlot.LEGS,net.minecraft.entity.EquipmentSlot.FEET)) {
          String part=switch(slot){case HEAD->"HELMET";case CHEST->"CHESTPLATE";case LEGS->"LEGGINGS";default->"BOOTS";};
          boolean unmasked=Set.of("THOR","SORCERER_SUPREME").contains(data.getClassId());
          if(slot==net.minecraft.entity.EquipmentSlot.HEAD&&unmasked)p.equipStack(slot,ItemStack.EMPTY);
          else p.equipStack(slot,vn.svframe.svframeitems.SVFrameItems.generator().generate("hero_"+data.getClassId().toLowerCase(Locale.ROOT)+"_"+part.toLowerCase(Locale.ROOT),1));
        }
      }
      case "refill" -> {data.setMana(data.getMaxResource(PlayerResource.MANA),ResourceUpdateReason.COMMAND);data.setStamina(data.getMaxResource(PlayerResource.STAMINA),ResourceUpdateReason.COMMAND);data.getMMOPlayerData().getCooldownMap().clearAllCooldowns();}
      case "cast" -> {
        var session=HeroRuntime.session(p);if(session==null||!session.power.canRecast(value.toUpperCase(Locale.ROOT),HeroRuntime.tick))HITS.put(p.getUuid(),new ArrayList<>());report.put("manaBefore",data.getMana());report.put("staminaBefore",data.getStamina());
        var result=SVFrameMMO.skillRuntime().cast(data,value.toUpperCase(Locale.ROOT));report.put("accepted",result!=null&&result.isSuccessful());report.put("manaAfter",data.getMana());report.put("staminaAfter",data.getStamina());
      }
      case "input" -> HeroRuntime.input(p,Integer.parseInt(value));
      case "weapon" -> p.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND,vn.svframe.svframeitems.SVFrameItems.generator().generate("hero_thor_stormbreaker",1));
      case "back" -> {
        String id=value.equalsIgnoreCase("thor")?"hero_thor_cape":value.equalsIgnoreCase("strange")?"hero_sorcerer_supreme_cape":"";
        report.put("backEquip",setBack(p,id.isEmpty()?ItemStack.EMPTY:vn.svframe.svframeitems.SVFrameItems.generator().generate(id,1)));
      }
      case "save-flight" -> {var nbt=new net.minecraft.nbt.NbtCompound();p.writeNbt(nbt);report.put("savedAbilities",nbt.getCompound("abilities").toString());}
      case "change-class" -> {SVFrameMMO.skillBar().leave(data);report.put("classChanged",data.changeClass(SVFrameMMO.classes().get(value.toUpperCase(Locale.ROOT)),PlayerClassChangeEvent.Reason.GUI));}
      case "strike","incoming" -> {
        var target=p.getServerWorld().getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(6),e->e.getCommandTags().contains("hero_qa_target")).stream().findFirst().orElseThrow();
        if(action.equals("strike"))p.attack(target);
        else p.damage(p.getServerWorld().getDamageSources().mobAttack(target),value.isBlank()?2:Float.parseFloat(value));
      }
      case "health" -> p.setHealth(Math.clamp(Float.parseFloat(value),1,p.getMaxHealth()));
      case "inspect" -> {}
      default -> throw new IllegalArgumentException("Unknown hero QA action");
    }
    report.put("state",HeroRuntime.inspect(p));report.put("mana",data.getMana());report.put("stamina",data.getStamina());report.put("hits",HITS.getOrDefault(p.getUuid(),List.of()));
    report.put("health",p.getHealth());report.put("maxHealth",p.getMaxHealth());report.put("effects",p.getStatusEffects().stream().map(e->e.getEffectType().getIdAsString()).toList());
    report.put("attributes",Map.of("speed",p.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED),"attackSpeed",p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_SPEED),"armor",p.getAttributeValue(EntityAttributes.GENERIC_ARMOR),"attack",p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)));
    if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("bettercombat"))try {
      var registry=Class.forName("net.bettercombat.logic.WeaponRegistry");var weapon=registry.getMethod("getAttributes",ItemStack.class).invoke(null,p.getMainHandStack());
      if(weapon!=null)report.put("betterCombatWeapon",Map.of("category",weapon.getClass().getMethod("category").invoke(weapon),"rangeBonus",weapon.getClass().getMethod("rangeBonus").invoke(weapon),"attacks",java.lang.reflect.Array.getLength(weapon.getClass().getMethod("attacks").invoke(weapon))));
    }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot inspect installed Better Combat weapon profile",ex);}
    report.put("targets",p.getServerWorld().getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(40),e->e.getCommandTags().contains("hero_qa_target")).stream().map(t->Map.of("uuid",t.getUuidAsString(),"health",t.getHealth(),"stun",HeroControl.stunned(t),"root",HeroControl.rooted(t),"silence",HeroControl.silenced(t),"armor",t.getArmor(),"effects",t.getStatusEffects().stream().map(e->e.getEffectType().getIdAsString()).toList(),"position",List.of(t.getX(),t.getY(),t.getZ()))).toList());
    try{var path=Path.of("qa/heroes/"+p.getUuid()+"-"+action+".json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
    p.getServer().getCommandSource().sendFeedback(()->Text.literal(new com.google.gson.Gson().toJson(report)),false);return 1;
  }
  private static void clearTestAccessories(ServerPlayerEntity p) {
    if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("accessories"))setBack(p,ItemStack.EMPTY);
    if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("trinkets"))return;
    try {
      var api=Class.forName("dev.emi.trinkets.api.TrinketsApi");
      var optional=(Optional<?>)api.getMethod("getTrinketComponent",LivingEntity.class).invoke(null,p);
      if(optional.isEmpty())return;
      var type=Class.forName("dev.emi.trinkets.api.TrinketComponent");
      var groups=(Map<?,?>)type.getMethod("getInventory").invoke(optional.get());
      for(var group:groups.values())for(var inventory:((Map<?,?>)group).values()) {
        var slots=(net.minecraft.inventory.Inventory)inventory;
        for(int i=0;i<slots.size();i++)slots.setStack(i,ItemStack.EMPTY);
        slots.markDirty();
      }
    }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot reset native QA accessories",ex);}
  }
  private static Map<String,Object> setBack(ServerPlayerEntity p,ItemStack stack){
    try{
      if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("accessories")){
        var ref=Class.forName("io.wispforest.accessories.api.slot.SlotReference");var slot=ref.getMethod("of",LivingEntity.class,String.class,int.class).invoke(null,p,"back",0);
        ref.getMethod("setStack",ItemStack.class).invoke(slot,ItemStack.EMPTY);
        var api=Class.forName("io.wispforest.accessories.api.AccessoriesAPI");boolean allowed=stack.isEmpty()||(boolean)api.getMethod("canEquip",ItemStack.class,ref).invoke(null,stack,slot);
        boolean wrong=false;if(!stack.isEmpty()){var belt=ref.getMethod("of",LivingEntity.class,String.class,int.class).invoke(null,p,"belt",0);wrong=(boolean)api.getMethod("canEquip",ItemStack.class,ref).invoke(null,stack,belt);}
        boolean applied=allowed&&(boolean)ref.getMethod("setStack",ItemStack.class).invoke(slot,stack);return Map.of("backend","accessories","applied",applied,"wrongSlotCanEquip",wrong);
      }
      var api=Class.forName("dev.emi.trinkets.api.TrinketsApi");var type=Class.forName("dev.emi.trinkets.api.TrinketComponent");
      var component=(Optional<?>)api.getMethod("getTrinketComponent",LivingEntity.class).invoke(null,p);if(component.isEmpty())return Map.of("applied",false,"reason","no component");
      var groups=(Map<?,?>)type.getMethod("getInventory").invoke(component.get());var chest=(Map<?,?>)groups.get("chest");
      if(chest==null||!(chest.get("back") instanceof net.minecraft.inventory.Inventory slot)||slot.size()==0)return Map.of("applied",false,"reason","no back slot","groups",groups.keySet().toString());
      slot.setStack(0,ItemStack.EMPTY);
      var refType=Class.forName("dev.emi.trinkets.api.SlotReference");var ref=refType.getConstructor(Class.forName("dev.emi.trinkets.api.TrinketInventory"),int.class).newInstance(slot,0);
      boolean allowed=true;
      if(!stack.isEmpty()){
        var trinket=api.getMethod("getTrinket",net.minecraft.item.Item.class).invoke(null,stack.getItem());
        allowed=(boolean)Class.forName("dev.emi.trinkets.api.Trinket").getMethod("canEquip",ItemStack.class,refType,LivingEntity.class).invoke(trinket,stack,ref,p);
      }
      if(allowed){slot.setStack(0,stack);slot.markDirty();}
      return Map.of("backend","trinkets","applied",allowed,"slot",refType.getMethod("getId").invoke(ref));
    }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot equip QA back cape",e);}
  }
}
