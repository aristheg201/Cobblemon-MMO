package vn.svframe.fantasyhub.core;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;
import vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import java.nio.file.*;
import java.util.*;

/** Opt-in live catalog/slot/passive regression. Requires a high-level DK with purchased Cut. */
final class SkillCatalogQa {
    static int run(ServerPlayerEntity player){
        var data=SVFrameMMO.playerData().get(player);var report=new LinkedHashMap<String,Object>();
        check(report,"death_knight_account",data.getClassId().equals("DEATH_KNIGHT"));
        var entries=PlayerSkillCatalog.entries(data);
        report.put("catalogIds",entries.stream().map(PlayerSkillCatalog.Entry::id).toList());
        check(report,"seven_declared_class_skills",entries.stream().filter(e->e.origin()==PlayerSkillCatalog.Origin.CLASS&&e.id().startsWith("CLS_DEATH_KNIGHT_")).count()==7);
        check(report,"purchased_player_skill_preserved",entries.stream().anyMatch(e->e.id().equals("COBBLEMON_MOVE_CUT")&&e.learned()));
        check(report,"no_unowned_other_class_skills",entries.stream().noneMatch(e->e.id().contains("AMBERS")&&!e.learned()));
        var passive=PlayerSkillCatalog.owned(data,"CLS_DEATH_KNIGHT_CURSED_SEAL");
        var active=PlayerSkillCatalog.owned(data,"CLS_DEATH_KNIGHT_DEATH_SENTENCE");
        check(report,"source_passive_trigger",passive!=null&&passive.skill().getTrigger().name().equals("TIMER"));
        check(report,"passive_slot_formula",PlayerSkillCatalog.allowedSlots(data,passive).equals(List.of(1)));
        check(report,"active_slot_formula",PlayerSkillCatalog.allowedSlots(data,active).contains(2)&&!PlayerSkillCatalog.allowedSlots(data,active).contains(1));
        var previous=PlayerSkillCatalog.bindings(data).get(1);
        boolean rejected=false;
        try{PlayerSkillCatalog.bind(data,1,active.id());}catch(IllegalArgumentException expected){rejected=true;}
        check(report,"invalid_bind_rejected_without_mutation",rejected&&Objects.equals(previous,PlayerSkillCatalog.bindings(data).get(1)));
        PlayerSkillCatalog.bind(data,1,passive.id());
        rejected=false;
        try{SVFrameMMO.skillRuntime().castBound(data,1);}catch(IllegalArgumentException expected){rejected=true;}
        check(report,"manual_passive_cast_rejected",rejected);
        var cow=EntityType.COW.create(player.getServerWorld());
        if(cow==null)throw new IllegalStateException("Cannot create passive QA target");
        cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);cow.setHealth(100);cow.setAiDisabled(true);
        cow.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ()+3,0,0);player.getServerWorld().spawnEntity(cow);
        // Beyond the native 40-tick seal lifetime: only automatic TIMER refresh can keep it active.
        SVFrameLibFabricMod.schedule(61,()->{
            try{
                check(report,"bound_timer_passive_executes",ReferenceClassSkillRuntime.hit(player,cow,1)&&ReferenceClassSkillRuntime.markStacks(cow)==1);
            }finally{
                cow.discard();PlayerSkillCatalog.unbind(data,1);
                if(previous!=null)PlayerSkillCatalog.bind(data,1,previous.id());
                SVFrameMMO.playerData().savePlayer(data);save(report);
            }
        });
        return 1;
    }
    private static void check(Map<String,Object> report,String name,boolean passed){report.put(name,passed);if(!passed){save(report);throw new IllegalStateException("Skill catalog runtime QA failed: "+name);}}
    private static void save(Map<String,Object> report){try{Path path=Path.of("qa/skill-catalog-runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
