package vn.svframe.svframelib.fabric;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.SVFrameLib;
import java.util.*;
import java.nio.file.*;

/** Opt-in source-timing regression performed against a real player and real damageable entity. */
final class DeathSentenceRuntimeQa {
    static int run(ServerPlayerEntity player){
        if(!HeroEntranceRuntimeQa.enabled())return 0;
        var report=new LinkedHashMap<String,Object>();var world=player.getServerWorld();
        HeroEntranceRuntime.cancel(player);player.networkHandler.requestTeleport(0,65,0,0,40);
        SVFrameLibFabricMod.schedule(10,()->{
            check(report,"source_cast_started",ReferenceClassSkillRuntime.cast("CLS_DEATH_KNIGHT_DEATH_SENTENCE",player,Map.of("damage",8d)));
            check(report,"warmup",HeroEntranceRuntime.view(player).phase()==HeroEntranceRuntime.Phase.WARMUP);
            SVFrameLibFabricMod.schedule(32,()->check(report,"launch",HeroEntranceRuntime.view(player)!=null&&HeroEntranceRuntime.view(player).phase()==HeroEntranceRuntime.Phase.LAUNCH&&player.getY()>65));
            SVFrameLibFabricMod.schedule(48,()->{
                var first=HeroEntranceRuntime.view(player);check(report,"hover",first!=null&&first.phase()==HeroEntranceRuntime.Phase.AIM);
                check(report,"source_hover_cannot_be_skipped",!HeroEntranceRuntime.confirm(player));
                var target=first.target();player.networkHandler.requestTeleport(player.getX(),player.getY(),player.getZ(),60,55);
                SVFrameLibFabricMod.schedule(5,()->{var second=HeroEntranceRuntime.view(player);check(report,"camera_moves_ring",second!=null&&second.target()!=null&&target!=null&&second.target().squaredDistanceTo(target)>1);});
            });
            SVFrameLibFabricMod.schedule(99,()->{
                var view=HeroEntranceRuntime.view(player);check(report,"original_hover_duration",view!=null&&view.phase()==HeroEntranceRuntime.Phase.AIM);
                var cow=EntityType.COW.create(world);if(cow==null)throw new IllegalStateException("Cannot create QA target");
                cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);cow.setHealth(100);cow.setAiDisabled(true);cow.refreshPositionAndAngles(view.target().x,view.target().y,view.target().z,0,0);world.spawnEntity(cow);
                SVFrameLibFabricMod.schedule(14,()->{
                    check(report,"one_impact",cow.getHealth()<100&&cow.getHealth()>0);
                    check(report,"knockup",cow.getVelocity().y>0||cow.getY()>view.target().y+.5);
                    check(report,"cast_finished",HeroEntranceRuntime.view(player)==null);check(report,"gravity_restored",!player.hasNoGravity());
                    cow.discard();save(report);
                });
            });
        });return 1;
    }
    private static void check(Map<String,Object> report,String name,boolean passed){report.put(name,passed);if(!passed){save(report);throw new IllegalStateException("Death Sentence runtime QA failed: "+name);}}
    private static void save(Map<String,Object> report){try{Path path=Path.of("qa/death-sentence-runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
