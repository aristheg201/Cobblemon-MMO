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
import java.nio.file.*;
import java.util.*;

/** Opt-in real-world scenarios cast the original Hunter wrappers through MMO's runtime. */
final class HunterQa {
    private static final Map<String,Object> results=new LinkedHashMap<>();
    private static final List<String> SKILLS=List.of("ENDURANCE","AARD","IGNI","YRDEN","QUEN","ADRENALINE_RUSH","BLACKHOLE");
    private static String id(String skill){return "CLS_HUNTER_HUNTER_"+skill;}
    static int run(ServerPlayerEntity player,String scenario) {
        var data=SVFrameMMO.playerData().get(player);
        if(scenario.equals("prepare")) {
            results.clear();player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);player.clearStatusEffects();
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))player.equipStack(slot,net.minecraft.item.ItemStack.EMPTY);
            data.changeClass(SVFrameMMO.classes().get("HUNTER"),PlayerClassChangeEvent.Reason.GUI);data.setLevel(30);
            Map<String,Object> support=new LinkedHashMap<>();results.put("source_wrappers",support);
            for(int i=0;i<SKILLS.size();i++) {
                String name=id(SKILLS.get(i));var skill=data.getProfess().getSkill(name);data.setSkillLevel(name,1);
                support.put(name,skill!=null&&skill.getSkill() instanceof NativeSkillProviders.BackedHandler backed&&NativeSkillProviders.available(backed.providerNamespace(),backed.sourceSkill()));
                PlayerSkillCatalog.bind(data,i+1,name);
            }
            results.put("class_skill_count",data.getProfess().getConfiguredSkills().size());
            SVFrameLibFabricMod.schedule(100,()->{results.put("automatic_endurance",player.hasStatusEffect(StatusEffects.RESISTANCE)&&player.hasStatusEffect(StatusEffects.REGENERATION)&&player.hasStatusEffect(StatusEffects.SPEED));save();});
            player.setHealth(player.getMaxHealth());SVFrameMMO.playerData().savePlayer(data);save();return 1;
        }
        if(!data.getClassId().equals("HUNTER"))throw new IllegalStateException("Prepare Hunter first");
        if(scenario.equals("targets"))return targets(player);
        if(scenario.startsWith("armor_"))return armor(player,scenario.substring(6));
        String name=scenario.toUpperCase(Locale.ROOT);if(!SKILLS.contains(name))throw new IllegalArgumentException("Unknown Hunter scenario");
        player.clearStatusEffects();player.setHealth(player.getMaxHealth());player.setYaw(0);player.setPitch(0);player.networkHandler.requestTeleport(player.getX(),player.getY(),player.getZ(),0,0);
        var origin=player.getPos();var skill=data.getProfess().getSkill(id(name));MMOPlayerData.setup(player).getCooldownMap().resetCooldown(skill.getCooldownPath());
        Map<String,Object> report=new LinkedHashMap<>();results.put(scenario,report);double mana=data.getMana(),stamina=data.getStamina();float before=player.getHealth();
        CowEntity target=Set.of("AARD","IGNI","YRDEN","BLACKHOLE").contains(name)?cow(player,name.equals("YRDEN")?1.5:5.5):null;
        report.put("cast_accepted",SVFrameMMO.skillRuntime().cast(data,id(name)).isSuccessful());
        report.put("zero_source_resource_cost",Math.abs(mana-data.getMana())<.00001&&Math.abs(stamina-data.getStamina())<.00001);
        report.put("cooldown_applied",MMOPlayerData.setup(player).getCooldownMap().getCooldown(skill.getCooldownPath())>0);
        if(name.equals("ADRENALINE_RUSH")) {
            report.put("self_damage_actual",player.getHealth()<before);report.put("before",before);report.put("after",player.getHealth());
            report.put("distinct_source_buffs",player.hasStatusEffect(StatusEffects.POISON)&&player.hasStatusEffect(StatusEffects.STRENGTH)&&player.getStatusEffect(StatusEffects.STRENGTH).getAmplifier()==1&&player.hasStatusEffect(StatusEffects.SPEED));
        }
        if(name.equals("QUEN")) {
            boolean damaged=player.damage(player.getDamageSources().generic(),4);report.put("actual_immunity",!damaged&&player.getHealth()==before);
            player.setPosition(origin.add(2,0,0));player.setYaw(70);
            SVFrameLibFabricMod.schedule(2,()->{report.put("stun_position_locked",player.squaredDistanceTo(origin)<.00001);report.put("facing_remains_free",Math.abs(player.getYaw()-70)<.1);save();});
            SVFrameLibFabricMod.schedule(125,()->{player.removeStatusEffect(StatusEffects.RESISTANCE);player.timeUntilRegen=0;float health=player.getHealth();boolean accepted=player.damage(player.getDamageSources().generic(),2);report.put("immunity_expires",accepted&&player.getHealth()<health);player.setPosition(origin.add(1,0,0));
                SVFrameLibFabricMod.schedule(2,()->{report.put("stun_expires",player.squaredDistanceTo(origin)>.5);save();});});
        }
        if(name.equals("BLACKHOLE")) {
            SVFrameLibFabricMod.schedule(3,()->{var destination=origin.add(0,2.1,6);report.put("pull_towards_fixed_origin",target.getVelocity().dotProduct(destination.subtract(target.getPos()))>0);report.put("pull_velocity",target.getVelocity().toString());player.networkHandler.requestTeleport(origin.x+20,origin.y,origin.z,180,0);save();});
        }
        SVFrameLibFabricMod.schedule(name.equals("BLACKHOLE")?125: name.equals("IGNI")?100:30,()->{
            if(target!=null) {
                report.put("target_health",target.getHealth());
                if(name.equals("YRDEN")){report.put("source_debuffs",target.hasStatusEffect(StatusEffects.SLOWNESS)&&target.hasStatusEffect(StatusEffects.BLINDNESS)&&target.hasStatusEffect(StatusEffects.WEAKNESS));report.put("source_has_no_damage",target.getHealth()==500);}
                else report.put("actual_damage",target.getHealth()<500);
                if(name.equals("AARD"))report.put("actual_throw",target.squaredDistanceTo(origin)>60);
                if(name.equals("IGNI"))report.put("actual_ignite",target.isOnFire());
                target.discard();
            }
            report.put("completed",true);save();
        });save();return 1;
    }
    private static int targets(ServerPlayerEntity player) {
        var data=SVFrameMMO.playerData().get(player);var report=new LinkedHashMap<String,Object>();results.put("targeting_and_upgrade",report);
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))player.equipStack(slot,net.minecraft.item.ItemStack.EMPTY);
        vn.svframe.svframeitems.SVFrameItems.equipment().refresh(player,true);
        var origin=player.getPos();List<LivingEntity> excluded=new ArrayList<>();
        for(int i=0;i<35;i++) {
            var stand=EntityType.ARMOR_STAND.create(player.getServerWorld());if(stand==null)throw new IllegalStateException("Cannot create excluded selector fixture");
            stand.refreshPositionAndAngles(origin.x,origin.y,origin.z+1+i*.01,0,0);player.getServerWorld().spawnEntity(stand);excluded.add(stand);
        }
        var target=cow(player,5.5);data.setSkillLevel(id("AARD"),1);var skill=data.getProfess().getSkill(id("AARD"));MMOPlayerData.setup(player).getCooldownMap().resetCooldown(skill.getCooldownPath());
        report.put("cast_accepted",SVFrameMMO.skillRuntime().cast(data,id("AARD")).isSuccessful());
        report.put("excluded_entities_do_not_consume_limit",target.getHealth()<500);report.put("armor_stands_excluded",excluded.stream().allMatch(e->e.getHealth()==e.getMaxHealth()));report.put("level_one_damage",500-target.getHealth());
        excluded.forEach(Entity::discard);target.discard();
        int previous=data.getSkillLevel(id("AARD")),points=data.getSkillPoints();data.giveSkillPoints(29);
        report.put("catalog_upgrade_29_levels",PlayerSkillCatalog.upgrade(data,PlayerSkillCatalog.owned(data,id("AARD")),29)==29);
        report.put("points_spent_once",data.getSkillPoints()==points);report.put("skill_reaches_original_max_level",data.getSkillLevel(id("AARD"))==30);
        SVFrameLibFabricMod.schedule(20,()->{
            CowEntity inside=cow(player,21.9),outside=cow(player,22.1);MMOPlayerData.setup(player).getCooldownMap().resetCooldown(skill.getCooldownPath());
            report.put("upgraded_cast_accepted",SVFrameMMO.skillRuntime().cast(data,id("AARD")).isSuccessful());
            report.put("original_level_scaling",Math.abs((500-inside.getHealth())-28.85)<.001);report.put("level_thirty_damage",500-inside.getHealth());
            report.put("range_boundary",inside.getHealth()<500&&outside.getHealth()==500);inside.discard();outside.discard();data.setSkillLevel(id("AARD"),previous);SVFrameMMO.playerData().savePlayer(data);save();
        });save();return 1;
    }
    private static CowEntity cow(ServerPlayerEntity player,double forward) {
        CowEntity cow=EntityType.COW.create(player.getServerWorld());if(cow==null)throw new IllegalStateException("Cannot spawn QA target");
        cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(500);cow.setHealth(500);cow.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0);
        cow.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ()+forward,0,0);player.getServerWorld().spawnEntity(cow);return cow;
    }
    private static int armor(ServerPlayerEntity player,String collection) {
        if(!Set.of("halfmoon","feline","griffin","ursine","kaer_morhen","wolven").contains(collection))throw new IllegalArgumentException("Unknown Hunter armor");
        var report=new LinkedHashMap<String,Object>();results.put("armor_"+collection,report);
        String prefix=collection.equals("halfmoon")?"SVFRAME_HUNTER_":"WITCHER_"+collection.toUpperCase(Locale.ROOT)+"_";
        var slots=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);var parts=List.of("HELMET","CHESTPLATE","LEGGINGS","BOOTS");
        for(int i=0;i<4;i++)player.equipStack(slots.get(i),vn.svframe.svframeitems.SVFrameItems.generator().generate(prefix+parts.get(i),1));
        vn.svframe.svframeitems.SVFrameItems.equipment().refresh(player,true);var view=vn.svframe.svframeitems.SVFrameItems.equipment().evaluate(player);
        report.put("four_original_base_pieces",view.items().size()==4&&view.items().stream().allMatch(i->i.instance().upgradeLevel()==0));
        double bonus=view.activeSetBonuses().stream().filter(s->s.stat().equals("MAX_HEALTH")).mapToDouble(s->s.value()).sum();
        report.put("source_set_bonus",Math.abs(bonus-(collection.equals("halfmoon")?48:0))<.00001);report.put("actual_max_health",player.getMaxHealth());
        float max=player.getMaxHealth();vn.svframe.svframeitems.SVFrameItems.equipment().refresh(player,true);report.put("idempotent_attributes",Math.abs(player.getMaxHealth()-max)<.00001);
        player.setHealth(player.getMaxHealth());SVFrameMMO.playerData().savePlayer(SVFrameMMO.playerData().get(player));save();return 1;
    }
    private static void save(){try{Path path=Path.of("qa/hunter-runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(results));}catch(java.io.IOException error){throw new IllegalStateException(error);}}
}
