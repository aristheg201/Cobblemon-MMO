package vn.svframe.svframemmo.cobblemon.validation;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;

/** Exercises the installed original Skript function against real Cobblemon ownership and battle state. */
public final class CobblemonSkriptRuntimeQa {
    private CobblemonSkriptRuntimeQa(){}
    public static int run(ServerPlayerEntity player){
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))||!FabricLoader.getInstance().isModLoaded("svframeskript"))throw new IllegalStateException("Skript runtime QA is not available");
        var report=new LinkedHashMap<String,Object>();var world=player.getServerWorld();var party=Cobblemon.INSTANCE.getStorage().getParty(player);
        if(com.cobblemon.mod.common.battles.BattleRegistry.getBattleByParticipatingPlayer(player)!=null)throw new IllegalStateException("Finish the existing battle before starting this QA fixture");
        var owned=PokemonProperties.Companion.parse("pikachu level=50").create();
        if(!party.add(owned))throw new IllegalStateException("A free QA party slot is required");
        var spawned=new ArrayList<PokemonEntity>();
        try{
            var wild=PokemonProperties.Companion.parse("pikachu level=50").createEntity(world);wild.setAiDisabled(true);wild.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ()+3,0,0);world.spawnEntity(wild);spawned.add(wild);
            float wildBefore=wild.getHealth();source(player,wild);check(report,"original_skript_damages_wild_pokemon",wild.getHealth()<wildBefore);report.put("wildHealthBefore",wildBefore);report.put("wildHealthAfter",wild.getHealth());wild.discard();
            var entity=owned.sendOut(world,player.getPos().add(0,0,3),null,ignored->kotlin.Unit.INSTANCE);entity.setAiDisabled(true);spawned.add(entity);
            float ownedBefore=entity.getHealth();source(player,entity);check(report,"original_skript_preserves_owned_health",entity.getHealth()==ownedBefore);check(report,"original_skript_preserves_owner",player.getUuid().equals(owned.getOwnerUUID()));entity.discard();
            var opponent=PokemonProperties.Companion.parse("gastly level=10").createEntity(world);opponent.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ()+3,0,0);world.spawnEntity(opponent);spawned.add(opponent);
            boolean started=opponent.forceBattle(player);
            SVFrameLibFabricMod.schedule(40,()->{
                try{
                    check(report,"actual_formal_battle_started",started&&opponent.getBattle()!=null);
                    float before=opponent.getHealth();source(player,opponent);check(report,"original_skript_preserves_battle_health",opponent.getHealth()==before);
                    check(report,"battle_remains_active_after_script",opponent.getBattle()!=null);report.put("battleHealthBefore",before);report.put("battleHealthAfter",opponent.getHealth());
                    var bystander=PokemonProperties.Companion.parse("pikachu level=50").createEntity(world);bystander.setAiDisabled(true);bystander.refreshPositionAndAngles(player.getX(),player.getY(),player.getZ()+1,0,0);world.spawnEntity(bystander);spawned.add(bystander);
                    float bystanderBefore=bystander.getHealth();source(player,bystander);report.put("bystanderHealthBefore",bystanderBefore);report.put("bystanderHealthAfter",bystander.getHealth());
                    check(report,"battle_trainer_cannot_cast_overworld_damage",bystander.getHealth()==bystanderBefore);
                    for(var effect:vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.values())check(report,"battle_trainer_blocks_"+effect.name().toLowerCase(Locale.ROOT),!vn.svframe.svframelib.entity.RpgEntityAdapters.allows(player,bystander,effect));
                }finally{if(opponent.getBattle()!=null)opponent.getBattle().end();spawned.forEach(PokemonEntity::discard);party.remove(owned);save(report);}
            });return 1;
        }catch(RuntimeException failure){spawned.forEach(PokemonEntity::discard);party.remove(owned);save(report);throw failure;}
    }
    private static void source(ServerPlayerEntity player,PokemonEntity target){
        var runtime=vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.nativeRuntime();
        if(runtime==null||!runtime.hasFunction("svamk_deal_damage"))throw new IllegalStateException("The original Skript source function is unavailable");
        runtime.invokeFunction("svamk_deal_damage",List.of(player,target,4d));
    }
    private static void check(Map<String,Object> report,String key,boolean passed){report.put(key,passed);if(!passed)throw new IllegalStateException("Skript/Cobblemon runtime regression: "+key);}
    private static void save(Map<String,Object> report){try{Path path=Path.of("qa/skript/cobblemon-source-runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
