package vn.svframe.svframelib.fabric;

import com.google.gson.GsonBuilder;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.SVFrameLib;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in verification of the reference class adapter through real vanilla damage and entities. */
public final class ReferenceClassRuntimeQa {
    private ReferenceClassRuntimeQa() { }
    public static int run(ServerPlayerEntity player) {
        if(!HeroEntranceRuntimeQa.enabled())return 0;
        Map<String,Object> report=new LinkedHashMap<>();
        player.networkHandler.requestTeleport(0,65,0,0,0);
        SVFrameLibFabricMod.schedule(10,()->{
            var world=player.getServerWorld();
            CowEntity cow=EntityType.COW.create(world);
            if(cow==null)throw new IllegalStateException("Missing QA cow");
            cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);cow.setHealth(100);cow.setAiDisabled(true);
            cow.refreshPositionAndAngles(0,65,3,0,0);world.spawnEntity(cow);
            check(report,"fourteen_handlers",SVFrameLib.inst().getSkills().getHandlers().stream().filter(h->ReferenceClassSkillRuntime.supports(h.getId())).count()==14);
            check(report,"unknown_rejected",!ReferenceClassSkillRuntime.canCast("UNSUPPORTED",player));
            check(report,"caster_excluded",!ReferenceClassSkillRuntime.canTarget(player,player));
            check(report,"cursed_passive",ReferenceClassSkillRuntime.cast("CLS_DEATH_KNIGHT_CURSED_SEAL",player,Map.of()));
            for(int i=0;i<4;i++){cow.timeUntilRegen=0;check(report,"attributed_hit_"+i,ReferenceClassSkillRuntime.hit(player,cow,1));}
            check(report,"four_curse_stacks",ReferenceClassSkillRuntime.markStacks(cow)==4);
            check(report,"full_seal_weakness",cow.hasStatusEffect(StatusEffects.WEAKNESS));
            check(report,"execution_bonus",ReferenceClassSkillRuntime.executionDamage(player,cow,8)==26);
            player.setHealth(Math.max(1,player.getMaxHealth()-10));float before=player.getHealth();player.timeUntilRegen=0;
            check(report,"barrier_cast",ReferenceClassSkillRuntime.cast("CLS_DEATH_KNIGHT_SOUL_BARRIER",player,Map.of("duration",.1)));
            player.damage(world.getDamageSources().mobAttack(cow),3);
            check(report,"barrier_converts_damage_to_heal",player.getHealth()>before);
            SVFrameLibFabricMod.schedule(5,()->{
                float start=player.getHealth();player.timeUntilRegen=0;
                check(report,"amk_passive",ReferenceClassSkillRuntime.cast("CLS_ANTI_MAGE_KNIGHT_PASSIVE",player,Map.of()));
                player.damage(world.getDamageSources().mobAttack(cow),1);
                check(report,"amk_shield_absorbs",player.getHealth()>=start);
                cow.timeUntilRegen=0;cow.setHealth(100);
                check(report,"gust_cast",ReferenceClassSkillRuntime.cast("CLS_ANTI_MAGE_KNIGHT_NORTHERN_GUST",player,Map.of()));
                SVFrameLibFabricMod.schedule(35,()->{
                    check(report,"gust_damages",cow.getHealth()<100);
                    check(report,"guard_cast",ReferenceClassSkillRuntime.cast("CLS_ANTI_MAGE_KNIGHT_OVERLOAD",player,Map.of()));
                    check(report,"duplicate_guard_rejected",!ReferenceClassSkillRuntime.canCast("CLS_ANTI_MAGE_KNIGHT_OVERLOAD",player));
                    player.timeUntilRegen=0;float guardHealth=player.getHealth();
                    player.damage(world.getDamageSources().mobAttack(cow),5);
                    check(report,"guard_reduces_damage",guardHealth-player.getHealth()<5);
                    cow.setVelocity(Vec3d.ZERO);cow.refreshPositionAndAngles(0,65,3,0,0);cow.setHealth(100);cow.timeUntilRegen=0;
                    SVFrameLibFabricMod.schedule(125,()->{
                        check(report,"guard_releases_stored_damage",cow.getHealth()<100);
                        cow.discard();save(report);
                    });
                });
            });
        });
        return 1;
    }
    private static void check(Map<String,Object> report,String key,boolean passed){report.put(key,passed);if(!passed){save(report);throw new IllegalStateException("Reference class QA failed: "+key);}}
    private static void save(Map<String,Object> report){try{Path path=Path.of("qa/reference-classes-runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));java.util.logging.Logger.getLogger("SVFrameLib-ReferenceQA").info("REFERENCE_QA_REPORT "+report);}catch(java.io.IOException e){throw new IllegalStateException(e);}}
}
