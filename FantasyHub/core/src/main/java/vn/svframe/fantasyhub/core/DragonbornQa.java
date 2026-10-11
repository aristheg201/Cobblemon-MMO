package vn.svframe.fantasyhub.core;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframelib.skill.NativeSkillProviders;
import java.util.*;
import java.nio.file.*;

/** Opt-in conformance scenarios exercise the real source wrapper and server graph, not direct effect substitutes. */
final class DragonbornQa {
    private static final Map<String,Object> results=new LinkedHashMap<>();
    private static final List<String> skills=List.of("DRAGON_SLAYER","UNRELENTING_FORCE","FIRE_BREATH","WHIRLWIND_SPRINT","BECOME_ETHEREAL","BATTLE_CRY","STORM_CALL");
    private static String id(String name){return "CLS_DRAGONBORN_"+name;}
    static int run(ServerPlayerEntity player,String scenario) {
        var data=SVFrameMMO.playerData().get(player);
        if(scenario.equals("prepare")){
            results.clear();data.changeClass(SVFrameMMO.classes().get("DRAGONBORN"),PlayerClassChangeEvent.Reason.GUI);data.setLevel(30);
            boolean available=true;
            for(String name:skills){var skill=data.getProfess().getSkill(id(name));available&=skill!=null&&skill.getSkill() instanceof NativeSkillProviders.BackedHandler backed&&NativeSkillProviders.available(backed.providerNamespace(),backed.sourceSkill());data.setSkillLevel(id(name),1);}
            results.put("seven_source_wrappers_available",available);results.put("class_skill_count",data.getProfess().getConfiguredSkills().size());
            PlayerSkillCatalog.bind(data,1,id("DRAGON_SLAYER"));for(int i=1;i<skills.size();i++)PlayerSkillCatalog.bind(data,i+1,id(skills.get(i)));
            SVFrameLibFabricMod.schedule(85,()->{results.put("automatic_dragon_slayer",player.hasStatusEffect(StatusEffects.STRENGTH)&&player.hasStatusEffect(StatusEffects.RESISTANCE)&&player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE));save();});
            SVFrameMMO.playerData().savePlayer(data);save();return 1;
        }
        if(!data.getClassId().equals("DRAGONBORN"))throw new IllegalStateException("Run Dragonborn prepare first");
        if(scenario.equals("armor")||scenario.equals("armor_helmet")||scenario.equals("armor_legacy"))return armor(player,scenario);
        String name=switch(scenario){case "force"->"UNRELENTING_FORCE";case "fire"->"FIRE_BREATH";case "sprint"->"WHIRLWIND_SPRINT";case "ethereal"->"BECOME_ETHEREAL";case "battlecry"->"BATTLE_CRY";case "storm"->"STORM_CALL";default->throw new IllegalArgumentException("Unknown Dragonborn QA scenario");};
        player.setYaw(0);player.setPitch(0);player.networkHandler.requestTeleport(player.getX(),player.getY(),player.getZ(),0,0);
        var skill=data.getProfess().getSkill(id(name));MMOPlayerData.setup(player).getCooldownMap().resetCooldown(skill.getCooldownPath());
        Map<String,Object> report=new LinkedHashMap<>();results.put(scenario,report);double mana=data.getMana(),stamina=data.getStamina();
        var origin=player.getPos();List<CowEntity> targets=new ArrayList<>();
        if(Set.of("force","fire","sprint").contains(scenario))targets.add(cow(player,0,scenario.equals("sprint")?4:6));
        if(scenario.equals("storm"))for(double[] offset:new double[][]{{0,7},{-3,10},{3,10},{0,13},{-4,14},{4,14},{0,17}})targets.add(cow(player,offset[0],offset[1]));
        report.put("cast_accepted",SVFrameMMO.skillRuntime().cast(data,id(name)).isSuccessful());
        report.put("zero_source_resource_cost",Math.abs(mana-data.getMana())<.0001&&Math.abs(stamina-data.getStamina())<.0001);
        report.put("cooldown_applied",MMOPlayerData.setup(player).getCooldownMap().getCooldown(skill.getCooldownPath())>0);
        report.put("repeat_rejected",!SVFrameMMO.skillRuntime().cast(data,id(name)).isSuccessful());
        if(scenario.equals("ethereal")){
            SVFrameLibFabricMod.schedule(25,()->{float before=player.getHealth();boolean accepted=player.damage(player.getDamageSources().generic(),4);report.put("damage_cancelled",!accepted&&player.getHealth()==before);report.put("invisibility_present",player.hasStatusEffect(StatusEffects.INVISIBILITY));save();});
            SVFrameLibFabricMod.schedule(125,()->{player.removeStatusEffect(StatusEffects.RESISTANCE);player.timeUntilRegen=0;float health=player.getHealth()+player.getAbsorptionAmount();boolean accepted=player.damage(player.getDamageSources().generic(),2);report.put("damage_returns_after_expiry",accepted&&player.getHealth()+player.getAbsorptionAmount()<health);report.put("invisibility_expired",!player.hasStatusEffect(StatusEffects.INVISIBILITY));save();});
        }
        if(scenario.equals("battlecry"))SVFrameLibFabricMod.schedule(2,()->{report.put("source_buff_package",player.hasStatusEffect(StatusEffects.STRENGTH)&&player.getStatusEffect(StatusEffects.STRENGTH).getAmplifier()==2&&player.hasStatusEffect(StatusEffects.REGENERATION)&&player.hasStatusEffect(StatusEffects.SPEED)&&player.hasStatusEffect(StatusEffects.ABSORPTION)&&player.hasStatusEffect(StatusEffects.LUCK)&&player.hasStatusEffect(StatusEffects.HERO_OF_THE_VILLAGE));report.put("absorption_actual",player.getAbsorptionAmount()>0);save();});
        SVFrameLibFabricMod.schedule(scenario.equals("fire")?75:60,()->{
            if(!targets.isEmpty()){report.put("all_source_targets_damaged",targets.stream().allMatch(t->t.getHealth()<500));report.put("target_health",targets.stream().map(CowEntity::getHealth).toList());}
            if(scenario.equals("force"))report.put("throw_actual",targets.getFirst().squaredDistanceTo(origin)>45);
            if(scenario.equals("fire"))report.put("burn_actual",targets.getFirst().isOnFire());
            if(scenario.equals("sprint")){report.put("dash_actual",player.squaredDistanceTo(origin)>49);report.put("burn_actual",targets.getFirst().isOnFire());report.put("distance",Math.sqrt(player.squaredDistanceTo(origin)));}
            if(scenario.equals("storm"))report.put("seven_distinct_strike_targets",targets.size()==7);
            for(var target:targets)target.discard();report.put("completed",true);save();
        });save();return 1;
    }
    private static CowEntity cow(ServerPlayerEntity player,double dx,double dz) {
        CowEntity cow=EntityType.COW.create(player.getServerWorld());if(cow==null)throw new IllegalStateException("Cannot spawn Dragonborn QA target");
        cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500);cow.setHealth(500);
        // Keep targets stationary without disabling their vanilla movement
        // physics; NoAI entities do not integrate throw velocity normally.
        cow.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
        cow.refreshPositionAndAngles(player.getX()+dx,player.getY(),player.getZ()+dz,0,0);player.getServerWorld().spawnEntity(cow);return cow;
    }
    private static int armor(ServerPlayerEntity player,String scenario){
        var report=new LinkedHashMap<String,Object>();results.put(scenario,report);
        var items=vn.svframe.svframeitems.SVFrameItems.registry();String prefix=scenario.equals("armor_legacy")?"SVFRAME_DRAGONBORN_":"SVFRAME_DRAGONBORN_ARMOR_";
        var suffixes=List.of(scenario.equals("armor")?"HAT":"HELMET","CHESTPLATE","LEGGINGS","BOOTS");
        var slots=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
        for(int i=0;i<4;i++)player.equipStack(slots.get(i),vn.svframe.svframeitems.SVFrameItems.generator().generate(prefix+suffixes.get(i),1));
        vn.svframe.svframeitems.SVFrameItems.equipment().refresh(player,true);
        var view=vn.svframe.svframeitems.SVFrameItems.equipment().evaluate(player);
        double bonus=view.activeSetBonuses().stream().filter(s->s.stat().equals("MAX_HEALTH")).mapToDouble(s->s.value()).sum();
        report.put("four_original_base_pieces",view.items().size()==4&&view.items().stream().allMatch(i->i.instance().upgradeLevel()==0));
        double resistance=view.items().stream().flatMap(i->i.effectiveStats().stream()).filter(s->s.stat().equals("KNOCKBACK_RESISTANCE")).mapToDouble(s->s.value()).sum();report.put("original_stat_precision",Math.abs(resistance-.12)<.0000001);
        report.put("cumulative_original_set_bonus",Math.abs(bonus-(scenario.equals("armor_legacy")?55:65))<.00001);
        report.put("set_health_bonus",bonus);report.put("actual_max_health",player.getMaxHealth());
        SVFrameLibFabricMod.schedule(2,()->{report.put("no_vanilla_material_bonus",player.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).getModifiers().stream().noneMatch(modifier->modifier.id().getNamespace().equals("minecraft")&&modifier.id().getPath().startsWith("armor.")));save();});
        vn.svframe.svframeitems.SVFrameItems.equipment().refresh(player,true);report.put("idempotent_max_health",player.getMaxHealth());
        var original=player.getMainHandStack().copy();var hat=player.getEquippedStack(EquipmentSlot.HEAD).copy();player.getInventory().setStack(player.getInventory().selectedSlot,hat);
        report.put("manual_drop_prevented",player.getInventory().dropSelectedItem(false).isEmpty()&&!player.getMainHandStack().isEmpty());
        report.put("enchanting_prevented",!hat.isEnchantable());report.put("unbreakable",hat.contains(net.minecraft.component.DataComponentTypes.UNBREAKABLE));
        player.getInventory().setStack(player.getInventory().selectedSlot,original);
        player.setHealth(player.getMaxHealth());SVFrameMMO.playerData().savePlayer(SVFrameMMO.playerData().get(player));save();return 1;
    }
    private static void save(){try{Path target=Path.of("qa/dragonborn-runtime.json");Files.createDirectories(target.getParent());Files.writeString(target,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));}catch(java.io.IOException error){throw new IllegalStateException(error);}}
}
