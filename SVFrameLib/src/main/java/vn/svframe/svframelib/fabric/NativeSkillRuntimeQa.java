package vn.svframe.svframelib.fabric;

import com.google.gson.GsonBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import vn.svframe.svframelib.fabric.runtime.script.ScriptPlatform;
import vn.svframe.svframelib.fabric.runtime.script.Vector3;
import java.nio.file.*;
import java.util.*;

/** Explicit opt-in arena test of wall collision, swept hits, dimension ownership and model cleanup. */
public final class NativeSkillRuntimeQa {
    private NativeSkillRuntimeQa() { }
    public static int run(ServerPlayerEntity player) {
        if (!HeroEntranceRuntimeQa.enabled()) return 0;
        ServerWorld world=player.getServerWorld();
        Map<String,Object> report=new LinkedHashMap<>();
        Vec3d start=player.getPos().add(0,1,0); float health=player.getHealth();
        CowEntity cow=cow(world,start.add(0,-1,6));
        BlockPos wall=BlockPos.ofFloored(start.add(0,0,3));
        var previous=world.getBlockState(wall);world.setBlockState(wall,Blocks.STONE.getDefaultState());
        FabricScriptPlatform platform=new FabricScriptPlatform(); int[] hits={0}, ends={0};
        ScriptPlatform.ProjectileSpec spec=new ScriptPlatform.ProjectileSpec(vector(start),new Vector3(0,0,1),8,12,.2,10);
        platform.projectile(player.getUuid(),spec,p -> {},id -> { hits[0]++;platform.damage(player.getUuid(),id,5,"MAGIC,SKILL"); },() -> ends[0]++);
        SVFrameLibFabricMod.schedule(4,() -> {
            check(report,"wall_blocks_projectile",cow.getHealth()==100 && hits[0]==0 && ends[0]==1);
            world.setBlockState(wall,previous);
            platform.projectile(player.getUuid(),spec,p -> {},id -> {hits[0]++;platform.damage(player.getUuid(),id,5,"MAGIC,SKILL");},() -> ends[0]++);
            SVFrameLibFabricMod.schedule(4,() -> {
                check(report,"swept_hit",cow.getHealth()<100 && hits[0]==1 && ends[0]==2);
                check(report,"caster_not_hit",player.getHealth()>=health);
                cow.discard();
                ServerWorld nether=player.getServer().getWorld(World.NETHER);
                boolean previouslyForced=nether.getForcedChunks().contains(net.minecraft.util.math.ChunkPos.toLong(0,0));
                nether.setChunkForced(0,0,true);nether.getChunk(0,0);
                SVFrameLibFabricMod.schedule(10,() -> {
                    CowEntity owner=cow(nether,new Vec3d(0,131,0)), target=cow(nether,new Vec3d(0,131,6));
                    SVFrameLibFabricMod.schedule(2,() -> {
                        platform.projectile(owner.getUuid(),new ScriptPlatform.ProjectileSpec(new Vector3(0,132,0),new Vector3(0,0,1),8,12,.2,10),p -> platform.particleAt(owner.getUuid(),p,"REDSTONE",1,0,0,0,0),id -> platform.damage(owner.getUuid(),id,5,"SKILL"),() -> {});
                        check(report,"model_spawned",NativeVisualRuntime.spawn("soul_blade",player,player.getPos().add(0,1,3),10,false));
                        check(report,"model_active",NativeVisualRuntime.activeCount()>0);
                        SVFrameLibFabricMod.schedule(16,() -> {
                            report.put("nether_target_health",target.getHealth());
                            check(report,"nether_projectile_hits_nether_entity",target.getHealth()==95);
                            check(report,"nether_caster_untouched",owner.getHealth()==100);
                            check(report,"model_expired",NativeVisualRuntime.activeCount()==0);
                            owner.discard();target.discard();
                            if (!previouslyForced) nether.setChunkForced(0,0,false);
                            testItems(player,report);save(report);
                        });
                    });
                });
            });
        });
        return 1;
    }
    private static void testItems(ServerPlayerEntity player,Map<String,Object> report) {
        var data=vn.svframe.svframelib.api.player.MMOPlayerData.setup(player);
        var mana=vn.svframe.svframelib.SVFrameLib.inst().getManaModule();
        double previousMana=mana.getMana(data);
        data.getCooldownMap().resetCooldown("hero_entrance");
        mana.setMana(data,2,vn.svframe.svframelib.player.resource.ResourceUpdateReason.SKILL);
        player.setOnGround(true);
        check(report,"item_insufficient_mana",!ItemSkillRuntime.cast("HERO_ENTRANCE",player,null,Map.of("mana",5,"cooldown",20)));
        mana.setMana(data,20,vn.svframe.svframelib.player.resource.ResourceUpdateReason.SKILL);
        check(report,"item_cast",ItemSkillRuntime.cast("HERO_ENTRANCE",player,null,Map.of("mana",5,"cooldown",20)));
        check(report,"item_mana_once",Math.abs(mana.getMana(data)-15)<.001);
        HeroEntranceRuntime.cancel(player);
        check(report,"shared_cooldown",data.getCooldownMap().isOnCooldown("hero_entrance"));
        check(report,"item_cooldown_rejects_recast",!ItemSkillRuntime.cast("hero_entrance",player,null,Map.of("mana",5,"cooldown",20)));
        check(report,"failed_item_cast_no_cost",Math.abs(mana.getMana(data)-15)<.001);
        data.getCooldownMap().resetCooldown("hero_entrance");
        mana.setMana(data,previousMana,vn.svframe.svframelib.player.resource.ResourceUpdateReason.SKILL);
    }
    private static Vector3 vector(Vec3d v) { return new Vector3(v.x,v.y,v.z); }
    private static CowEntity cow(ServerWorld world,Vec3d at) {
        CowEntity cow=Objects.requireNonNull(EntityType.COW.create(world));
        cow.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(100);cow.setHealth(100);cow.setAiDisabled(true);cow.setNoGravity(true);
        cow.refreshPositionAndAngles(at.x,at.y,at.z,0,0);world.spawnEntity(cow);return cow;
    }
    private static void check(Map<String,Object> report,String key,boolean value) { report.put(key,value);if(!value){save(report);throw new IllegalStateException("Native QA failed: "+key);} }
    private static void save(Map<String,Object> report) {
        try { Path path=Path.of("qa/native-skill-runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("NATIVE_SKILL_QA_REPORT="+report); }
        catch(Exception error){throw new IllegalStateException(error);}
    }
}
