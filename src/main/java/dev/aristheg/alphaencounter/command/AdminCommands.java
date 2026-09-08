package dev.aristheg.alphaencounter.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.aristheg.alphaencounter.AlphaEncounterMod;
import dev.aristheg.alphaencounter.runtime.ActiveEncounter;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class AdminCommands {
    private AdminCommands() {}

    public static void register(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("alphaencounter").requires(s -> s.hasPermissionLevel(2))
            .then(CommandManager.literal("help").executes(c -> help(c.getSource())))
            .then(CommandManager.literal("reload").executes(c -> { AlphaEncounterMod.RUNTIME.reloadConfig(); feedback(c.getSource(), "Alpha-Encounter gameplay config, messages, and bossbars reloaded."); return 1; }))
            .then(CommandManager.literal("save").executes(c -> { AlphaEncounterMod.RUNTIME.saveState(c.getSource().getServer()); feedback(c.getSource(), "Alpha-Encounter state saved."); return 1; }))
            .then(CommandManager.literal("debug").executes(c -> { feedback(c.getSource(), AlphaEncounterMod.RUNTIME.perfLine()); return 1; })
                .then(CommandManager.literal("reset").executes(c -> { AlphaEncounterMod.RUNTIME.resetCounters(); feedback(c.getSource(), "Counters reset."); return 1; })))
            .then(CommandManager.literal("list").executes(c -> list(c.getSource())))
            .then(CommandManager.literal("spawn").then(CommandManager.argument("id", StringArgumentType.word())
                .suggests((c,b) -> { AlphaEncounterMod.CONFIG.encounterIds().forEach(b::suggest); return b.buildFuture(); })
                .executes(c -> spawn(c.getSource(), StringArgumentType.getString(c,"id"), null))
                .then(CommandManager.argument("player", EntityArgumentType.player()).executes(c -> spawn(c.getSource(), StringArgumentType.getString(c,"id"), EntityArgumentType.getPlayer(c,"player"))))))
            .then(CommandManager.literal("inspect").then(CommandManager.argument("target", StringArgumentType.word()).executes(c -> inspect(c.getSource(), StringArgumentType.getString(c,"target")))))
            .then(CommandManager.literal("despawn").then(CommandManager.argument("target", StringArgumentType.word()).executes(c -> despawn(c.getSource(), StringArgumentType.getString(c,"target")))))
            .then(CommandManager.literal("defeat").then(CommandManager.argument("target", StringArgumentType.word()).executes(c -> defeat(c.getSource(), StringArgumentType.getString(c,"target")))))
            .then(CommandManager.literal("sethp").then(CommandManager.argument("target", StringArgumentType.word()).then(CommandManager.argument("percent", FloatArgumentType.floatArg(1f,100f)).executes(c -> setHp(c.getSource(), StringArgumentType.getString(c,"target"), FloatArgumentType.getFloat(c,"percent"))))))
            .then(CommandManager.literal("battle").then(CommandManager.argument("target", StringArgumentType.word()).then(CommandManager.argument("player", EntityArgumentType.player()).executes(c -> battle(c.getSource(), StringArgumentType.getString(c,"target"), EntityArgumentType.getPlayer(c,"player"))))))
            .then(CommandManager.literal("attack").then(CommandManager.argument("target", StringArgumentType.word()).then(CommandManager.argument("player", EntityArgumentType.player()).executes(c -> attack(c.getSource(), StringArgumentType.getString(c,"target"), EntityArgumentType.getPlayer(c,"player"))))))
            .then(CommandManager.literal("anim").then(CommandManager.argument("target", StringArgumentType.word()).then(CommandManager.argument("animation", StringArgumentType.word()).executes(c -> anim(c.getSource(), StringArgumentType.getString(c,"target"), StringArgumentType.getString(c,"animation"))))))
            .then(CommandManager.literal("reset").then(CommandManager.argument("id", StringArgumentType.word()).executes(c -> { String id=StringArgumentType.getString(c,"id"); AlphaEncounterMod.RUNTIME.resetCooldown(id); feedback(c.getSource(),"Reset cooldown: "+id); return 1; }))));
    }

    private static int help(ServerCommandSource s) {
        feedback(s,"/alphaencounter reload | save | debug [reset] | list");
        feedback(s,"/alphaencounter spawn <id> [player] | inspect <nearest|uuid|id>");
        feedback(s,"/alphaencounter despawn|defeat <target> | sethp <target> <1-100>");
        feedback(s,"/alphaencounter battle <target> <player> | attack <target> <player> | anim <target> <animation> | reset <id|all>");
        return 1;
    }
    private static int list(ServerCommandSource s){ var list=AlphaEncounterMod.RUNTIME.activeSorted(); feedback(s,"Active: "+list.size()); for(var a:list) feedback(s,AlphaEncounterMod.RUNTIME.inspectLine(s.getServer(),a)); return list.size(); }
    private static int spawn(ServerCommandSource s,String id,ServerPlayerEntity player){ ServerWorld w=player==null?s.getWorld():player.getServerWorld(); BlockPos p=player==null?BlockPos.ofFloored(s.getPosition()):player.getBlockPos(); ActiveEncounter a=AlphaEncounterMod.RUNTIME.spawnEncounter(id,w,p,true); if(a==null){feedback(s,"Spawn failed: "+id);return 0;} feedback(s,AlphaEncounterMod.RUNTIME.inspectLine(s.getServer(),a));return 1; }
    private static int inspect(ServerCommandSource s,String t){ ActiveEncounter a=AlphaEncounterMod.RUNTIME.resolveAdminTarget(s,t); if(a==null){feedback(s,"No matching encounter.");return 0;} feedback(s,AlphaEncounterMod.RUNTIME.inspectLine(s.getServer(),a)); return 1; }
    private static int despawn(ServerCommandSource s,String t){ boolean ok=AlphaEncounterMod.RUNTIME.adminDespawn(AlphaEncounterMod.RUNTIME.resolveAdminTarget(s,t)); feedback(s,ok?"Despawned.":"No matching encounter.");return ok?1:0; }
    private static int defeat(ServerCommandSource s,String t){ boolean ok=AlphaEncounterMod.RUNTIME.adminDefeat(s.getServer(),AlphaEncounterMod.RUNTIME.resolveAdminTarget(s,t)); feedback(s,ok?"Forced defeat.":"No matching encounter.");return ok?1:0; }
    private static int setHp(ServerCommandSource s,String t,float p){ boolean ok=AlphaEncounterMod.RUNTIME.adminSetHp(s.getServer(),AlphaEncounterMod.RUNTIME.resolveAdminTarget(s,t),p); feedback(s,ok?"HP set to "+p+"%.":"No matching encounter.");return ok?1:0; }
    private static int battle(ServerCommandSource s,String t,ServerPlayerEntity p){ boolean ok=AlphaEncounterMod.RUNTIME.adminBattle(AlphaEncounterMod.RUNTIME.resolveAdminTarget(s,t),p); feedback(s,ok?"Battle queued.":"Could not queue battle.");return ok?1:0; }
    private static int attack(ServerCommandSource s,String t,ServerPlayerEntity p){ boolean ok=AlphaEncounterMod.RUNTIME.adminAttack(s.getServer(),AlphaEncounterMod.RUNTIME.resolveAdminTarget(s,t),p); feedback(s,ok?"Vanilla field attack landed.":"Field attack failed.");return ok?1:0; }
    private static int anim(ServerCommandSource s,String t,String a){ boolean ok=AlphaEncounterMod.RUNTIME.adminAnimation(s.getServer(),AlphaEncounterMod.RUNTIME.resolveAdminTarget(s,t),a); feedback(s,ok?"Played animation "+a:"Animation failed.");return ok?1:0; }
    private static void feedback(ServerCommandSource s,String m){s.sendFeedback(()-> Text.literal(m),false);}
}
