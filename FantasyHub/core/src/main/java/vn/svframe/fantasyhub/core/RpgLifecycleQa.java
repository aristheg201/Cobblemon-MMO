package vn.svframe.fantasyhub.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.text.Text;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframelib.fabric.SVFrameLibFabricMod;
import vn.svframe.svframelib.fabric.SVFrameLibStatMod;

import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Opt-in real player regressions. These are admin test commands, never gameplay requests. */
public final class RpgLifecycleQa {
    private static final Map<UUID,String> LABELS=new HashMap<>();
    private RpgLifecycleQa(){}
    static String label(UUID id){return LABELS.getOrDefault(id,"snapshot");}
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher){
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))return;
        dispatcher.register(literal("fantasyhubqa").requires(s->s.hasPermissionLevel(2))
                .then(argument("scenario",StringArgumentType.word()).executes(ctx->run(ctx.getSource().getPlayerOrThrow(),StringArgumentType.getString(ctx,"scenario")))));
    }
    private static int run(ServerPlayerEntity player,String scenario){
        var data=SVFrameMMO.playerData().get(player);LABELS.put(player.getUuid(),scenario);
        switch(scenario){
            case "catalog" -> {return SkillCatalogQa.run(player);}
            case "prepare" -> {
                data.changeClass(SVFrameMMO.classes().get("DEATH_KNIGHT"),PlayerClassChangeEvent.Reason.GUI);
                data.setLevel(100);player.setHealth(player.getMaxHealth());SVFrameMMO.playerData().savePlayer(data);
            }
            case "direct", "skill", "potion", "group", "regeneration" -> {
                player.hurtTime=0;player.maxHurtTime=0;player.timeUntilRegen=0;
                player.setHealth(Math.max(1,player.getMaxHealth()-16));
                // Wait for the client's setup health update to settle before observing healing.
                LABELS.put(player.getUuid(),scenario+"_setup");
                SVFrameLibFabricMod.schedule(40,()->{
                    if(player.isDisconnected()||!player.isAlive())return;
                    LABELS.put(player.getUuid(),scenario+"_heal");
                    switch(scenario){
                        case "direct" -> player.heal(8);
                        case "skill" -> SVFrameLibFabricMod.castSkill("HEAL",player.getUuid(),player.getUuid(),Map.of("heal",8d));
                        case "potion" -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.INSTANT_HEALTH,1,1));
                        case "regeneration" -> player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,120,0));
                        case "group" -> {for(var ally:player.getServerWorld().getPlayers())if(ally.squaredDistanceTo(player)<100)ally.heal(8);}
                    }
                });
            }
            case "damage" -> {player.timeUntilRegen=0;player.damage(player.getDamageSources().generic(),3);}
            case "buff" -> {
                SVFrameLibStatMod.engine().registerTemporary(player.getUuid(),"MAX_HEALTH","qa_health",20,
                        NativeStatEngine.ModifierType.FLAT,NativeStatEngine.EquipmentSlot.OTHER,
                        NativeStatEngine.ModifierSource.OTHER,80,SVFrameLibFabricMod.currentTick());
                SVFrameLibFabricMod.schedule(80,()->LABELS.put(player.getUuid(),"buff_expired"));
            }
            case "snapshot" -> {}
            default -> {return 0;}
        }
        player.sendMessage(Text.literal("QA "+scenario+" maxHealth="+player.getMaxHealth()),false);
        return 1;
    }
}
