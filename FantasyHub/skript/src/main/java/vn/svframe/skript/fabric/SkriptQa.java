package vn.svframe.skript.fabric;

import java.util.*;
import java.nio.file.*;
import com.google.gson.GsonBuilder;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.skript.lang.*;

/** Opt-in observations of real client-driven jumps and native damage, never synthetic source events. */
final class SkriptQa {
    private static final Map<UUID,Map<String,Object>> checks=new HashMap<>();
    static void staminaChanged(ServerPlayerEntity player,double before,double after,vn.svframe.svframelib.player.resource.ResourceUpdateReason reason){
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))||reason!=vn.svframe.svframelib.player.resource.ResourceUpdateReason.SKILL)return;
        var record=checks.get(player.getUuid());if(record==null)return;
        record.put("lastSkillStaminaBefore",before);record.put("lastSkillStaminaAfter",after);record.put("skillStaminaDebited",((Number)record.getOrDefault("skillStaminaDebited",0d)).doubleValue()+before-after);
    }
    static int run(ServerPlayerEntity player,String action)throws java.io.IOException {
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))throw new IllegalStateException("Runtime QA is disabled");
        if(action.startsWith("combat_"))return SkriptCombatQa.run(player,action);
        Map<String,Object> record=checks.computeIfAbsent(player.getUuid(),id->new LinkedHashMap<>());
        switch(action){
            case "begin_case"->{record.clear();record.put("skillStaminaDebited",0d);}
            case "case_result"->{
                Path root=player.getServer().getRunDirectory().resolve("qa/skript");Files.createDirectories(root);Files.writeString(root.resolve("dash-case.json"),new GsonBuilder().setPrettyPrinting().create().toJson(record));
            }
            case "snapshot"->{
                var data=SVFrameMMO.playerData().get(player);Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("class",data.getClassId());snapshot.put("stamina",data.getStamina());snapshot.put("maxStamina",data.getMMOPlayerData().getStatMap().getStat("MAX_STAMINA"));snapshot.put("casting",SVFrameMMO.skillBar().isCasting(player.getUuid()));snapshot.put("health",player.getHealth());snapshot.put("maxHealth",player.getMaxHealth());snapshot.put("hurtTime",player.hurtTime);snapshot.put("velocity",List.of(player.getVelocity().x,player.getVelocity().y,player.getVelocity().z));record.put("snapshot",snapshot);
                player.sendMessage(Text.literal(new GsonBuilder().create().toJson(snapshot)),false);
            }
            case "dash_hit"->{
                if(!Boolean.TRUE.equals(SVFrameSkript.ENGINE.variables().get("svdash.iframe::"+player.getUuid(),Map.of())))throw new IllegalStateException("Perform a real walking dash immediately before this check");
                float health=player.getHealth();int hurt=player.hurtTime;boolean applied=player.damage(player.getDamageSources().generic(),4);
                record.put("realDamageCancelledDuringDash",!applied&&player.getHealth()==health&&player.hurtTime==hurt);record.put("dashTestHealth",health);record.put("dashTestStamina",SVFrameMMO.playerData().get(player).getStamina());
            }
            case "after_dash"->{
                if(Boolean.TRUE.equals(SVFrameSkript.ENGINE.variables().get("svdash.iframe::"+player.getUuid(),Map.of())))throw new IllegalStateException("Wait until the original twenty-tick dodge window expires");
                record.put("originalDodgeWindowExpired",true);float before=player.getHealth();boolean applied=player.damage(player.getDamageSources().generic(),4);
                record.put("legitimateDamageAfterDash",applied&&player.getHealth()<before&&player.hurtTime>0);
                player.heal(Math.max(0,before-player.getHealth()));
            }
            case "save"->{
                Path root=player.getServer().getRunDirectory().resolve("qa/skript");Files.createDirectories(root);Files.writeString(root.resolve("dash-runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("player",player.getName().getString(),"checks",record,"runtime",SVFrameSkript.report())));
                if(!List.of("realDamageCancelledDuringDash","originalDodgeWindowExpired","legitimateDamageAfterDash").stream().allMatch(key->Boolean.TRUE.equals(record.get(key))))throw new IllegalStateException("Native script gameplay regression failed or was not executed");
            }
            default->throw new IllegalArgumentException("Unknown native source QA stage");
        }
        return 1;
    }
}
