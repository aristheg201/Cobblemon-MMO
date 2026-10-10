package vn.svframe.svframemmo.cobblemon.validation;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframemmo.cobblemon.integration.*;
import vn.svframe.svframemmo.cobblemon.move.*;
import vn.svframe.svframemmo.SVFrameMMO;
import java.util.*;
import java.nio.file.*;

/** Native API/runtime proof. Only enabled explicitly for an administrator in the QA environment. */
public final class CobblemonEntityRuntimeQa {
    private CobblemonEntityRuntimeQa(){}
    public static int run(ServerPlayerEntity player){
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))return 0;
        var report=new LinkedHashMap<String,Object>();var world=player.getServerWorld();
        player.networkHandler.requestTeleport(0,65,0,0,0);
        var wild=PokemonProperties.Companion.parse("pikachu level=50").createEntity(world);wild.setAiDisabled(true);wild.refreshPositionAndAngles(0,65,4,0,0);world.spawnEntity(wild);wild.addCommandTag("svframe_qa");
        check(report,"actual_pokemon_entity",wild.getPokemon().getSpecies().getName().equalsIgnoreCase("pikachu"));
        check(report,"wild_eligible",RpgEntityAdapters.allows(player,wild,RpgEntityAdapters.Effect.DAMAGE));
        check(report,"live_type_chart",CobblemonTypeChart.ready());
        check(report,"ground_immunity",CobblemonTypeChart.effectiveness("electric",PokemonProperties.Companion.parse("sandshrew").create().getForm().getTypes())==0);
        SVFrameLibFabricMod.schedule(10,()->{
            float before=wild.getHealth();var definition=CobblemonMoveSkillAdapter.definitions().get("tackle");
            SVFrameMMO.skillRuntime().castTemporary(SVFrameMMO.playerData().get(player),definition);
            check(report,"player_move_hits_real_pokemon",wild.getHealth()<before);report.put("wildHealthBefore",before);report.put("wildHealthAfter",wild.getHealth());
            check(report,"actual_persistent_status",CobblemonEntityAdapter.INSTANCE.status(player,wild,"poison")&&wild.getPokemon().getStatus()!=null);
            var party=Cobblemon.INSTANCE.getStorage().getParty(player);var owned=PokemonProperties.Companion.parse("pikachu level=50").create();
            check(report,"ownership_via_real_store",party.add(owned));var entity=owned.sendOut(world,player.getPos().add(4,0,0),null,ignored->kotlin.Unit.INSTANCE);
            check(report,"owned_target_protected",!RpgEntityAdapters.allows(player,entity,RpgEntityAdapters.Effect.DAMAGE));
            check(report,"owned_actor_cannot_damage_owner",!RpgEntityAdapters.allows(entity,player,RpgEntityAdapters.Effect.DAMAGE));
            check(report,"owner_healing_legal",RpgEntityAdapters.heal(player,entity,3));
            check(report,"owner_retained",owned.getOwnerUUID().equals(player.getUuid()));
            var opponent=PokemonProperties.Companion.parse("gastly level=10").createEntity(world);opponent.refreshPositionAndAngles(0,65,7,0,0);world.spawnEntity(opponent);opponent.addCommandTag("svframe_qa");
            boolean started=opponent.forceBattle(player);report.put("formalBattleStarted",started);
            SVFrameLibFabricMod.schedule(40,()->{
                try{
                    check(report,"formal_battle_api_started",started&&opponent.getBattle()!=null);
                    check(report,"formal_battle_overworld_damage_rejected",!RpgEntityAdapters.allows(player,opponent,RpgEntityAdapters.Effect.DAMAGE));
                    check(report,"formal_battle_overworld_status_rejected",!RpgEntityAdapters.allows(player,opponent,RpgEntityAdapters.Effect.STATUS));
                    check(report,"formal_battle_overworld_heal_rejected",!RpgEntityAdapters.heal(player,opponent,2));
                    check(report,"formal_battle_actor_cannot_damage_player",!RpgEntityAdapters.allows(opponent,player,RpgEntityAdapters.Effect.DAMAGE));
                    check(report,"formal_battle_actor_cannot_heal_player",!RpgEntityAdapters.heal(opponent,player,2));
                    opponent.getBattle().end();
                }finally{wild.discard();opponent.discard();if(!entity.isRemoved())entity.discard();party.remove(owned);save(report);}
            });
        });return 1;
    }
    private static void check(Map<String,Object> report,String name,boolean passed){report.put(name,passed);if(!passed){save(report);throw new IllegalStateException("Cobblemon runtime QA failed: "+name);}}
    private static void save(Map<String,Object> report){try{Path path=Path.of("qa/cobblemon-entity-runtime.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}}
}
