package vn.svframe.skript.fabric;

import com.google.gson.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.slf4j.*;
import vn.svframe.skript.lang.*;
import vn.svframe.svframemmo.api.event.*;
import vn.svframe.svframelib.api.event.skill.PlayerCastSkillEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

public final class SVFrameSkript implements ModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("SVFrameSkript");
    public static final FabricPlatform PLATFORM=new FabricPlatform();
    public static final Engine ENGINE=new Engine(PLATFORM);
    private static final Path ROOT=FabricLoader.getInstance().getConfigDir().resolve("SVFrameSkript");
    private static final Map<String,Long> counters=new LinkedHashMap<>();
    private static final List<Map<String,Object>> failures=new ArrayList<>();
    private static final Map<UUID,String> screenTitles=new HashMap<>();
    public static String screenTitle(ServerPlayerEntity player){return screenTitles.getOrDefault(player.getUuid(),"Inventory");}
    public static void screenTitle(ServerPlayerEntity player,String title){screenTitles.put(player.getUuid(),title);}
    private static final Set<String> registeredCommands=new HashSet<>();
    private static boolean dirty;private static VariableStore store;private static MinecraftServer server;private static boolean active;
    @Override public void onInitialize(){
        CommandRegistrationCallback.EVENT.register((dispatcher,registries,environment)->dispatcher.register(literal("skript").requires(s->s.hasPermissionLevel(2))
            .then(literal("conformance").executes(context->{try{PLATFORM.requireExecutionThread();var result=ScriptPackConformance.inspect(ROOT.resolve("scripts"),ROOT.resolve("conformance.json"));context.getSource().sendFeedback(()->Text.translatable("svframeskript.conformance",result.get("individuallyCompiled"),result.get("packCompiled"),((List<?>)result.get("files")).size()),false);return 1;}catch(IOException failure){throw new IllegalStateException("Cannot write script conformance report",failure);}}))
            .then(literal("reload").executes(context->{reload();context.getSource().sendFeedback(()->Text.translatable("svframeskript.reload",validFiles(),ENGINE.program().files.size()),false);return 1;}))
            .then(literal("qa").requires(s->s.hasPermissionLevel(4)&&"1".equals(System.getenv("SVFRAME_RUNTIME_QA"))).then(argument("stage",StringArgumentType.word()).executes(c->{try{return SkriptQa.run(c.getSource().getPlayerOrThrow(),StringArgumentType.getString(c,"stage"));}catch(IOException e){throw new IllegalStateException(e);}})))
            .then(literal("diagnostics").executes(context->{context.getSource().sendFeedback(()->Text.literal(new Gson().toJson(report())),false);return 1;}))));
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;PLATFORM.server(s);registeredCommands.clear();counters.clear();failures.clear();screenTitles.clear();dirty=false;store=null;try{Files.createDirectories(ROOT.resolve("scripts"));store=new VariableStore(ROOT.resolve("variables.json"),s);ENGINE.variables().restore(store.load());ENGINE.variables().validateWith(VariableStore::validate);ENGINE.variables().onChange(()->dirty=true);active=true;reload();}catch(IOException e){LOG.error("Skript startup failed; execution remains disabled",e);active=false;}});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{boolean running=active;if(running)ENGINE.stop();active=false;vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.classPolicy(null);vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.nativeRuntime(null);vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime.scriptAdapter(null);if(store!=null)try{if(running)store.save(ENGINE.variables().snapshot());store.close();}catch(IOException e){LOG.error("Skript shutdown persistence failed",e);}});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(active){ENGINE.tick();if(dirty){store.save(ENGINE.variables().snapshot());dirty=false;}}});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,s)->{if(!active)return;ServerPlayerEntity player=handler.player;PLATFORM.remember(player);Map<String,Object> event=playerEvent(player);ENGINE.dispatch("join",event);Path data=s.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA).resolve(player.getUuid()+".dat");if(!Files.exists(data))ENGINE.dispatch("first join",event);});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,s)->{
            Runnable quit=()->{if(!active)return;dispatch("quit",playerEvent(handler.player));SkriptCombatQa.disconnected(handler.player);screenTitles.remove(handler.player.getUuid());};
            if(s.isOnThread())quit.run();else if(!s.isStopping())s.execute(quit);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->dispatch("respawn",playerEvent(newPlayer)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->dispatch("world change",playerEvent(player)));
        UseItemCallback.EVENT.register((player,world,hand)->net.minecraft.util.TypedActionResult.pass(player.getStackInHand(hand)));
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{if(player instanceof ServerPlayerEntity p){Map<String,Object> state=dispatch("right click",playerEvent(p));if(Boolean.TRUE.equals(state.get("cancelled")))return ActionResult.FAIL;}return ActionResult.PASS;});
        AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->{if(player instanceof ServerPlayerEntity p&&Boolean.TRUE.equals(dispatch("left click",playerEvent(p)).get("cancelled")))return ActionResult.FAIL;return ActionResult.PASS;});
        PlayerClassChangeEvent.EVENT.register(event->{if(event.getPlayer()==null)return;Map<String,Object> values=playerEvent(event.getPlayer());values.put("old class",event.getOldClass().getId());values.put("new class",event.getNewClass().getId());if(Boolean.TRUE.equals(dispatch("class selected",values).get("cancelled")))event.setCancelled(true);});
        PlayerCastSkillEvent.EVENT.register(event->{if(event.getPlayer()==null)return;Map<String,Object> values=playerEvent(event.getPlayer());values.put("skill",event.getCast().getHandler().getId());if(Boolean.TRUE.equals(dispatch("skill cast",values).get("cancelled")))event.setCancelled(true);});
        PlayerResourceUpdateEvent.EVENT.register(event->{if(event.getPlayer()==null||!event.isDecrease()||!event.getResource().name().equals("MANA"))return;Map<String,Object> values=playerEvent(event.getPlayer());values.put("amount",-event.getDifference());if(Boolean.TRUE.equals(dispatch("mana consumed",values).get("cancelled")))event.setCancelled(true);});
        PlayerLevelChangeEvent.EVENT.register(event->{if(event.getPlayer()!=null)dispatch("character level-up",playerEvent(event.getPlayer()));});
        PlayerEnterCastingModeEvent.EVENT.register(event->{if(event.getPlayer()!=null&&Boolean.TRUE.equals(dispatch("playerentercastingmodeevent",playerEvent(event.getPlayer())).get("cancelled")))event.setCancelled(true);});
        PlayerExitCastingModeEvent.EVENT.register(event->{if(event.getPlayer()!=null&&Boolean.TRUE.equals(dispatch("playerexitcastingmodeevent",playerEvent(event.getPlayer())).get("cancelled")))event.setCancelled(true);});
    }
    public static Map<String,Object> playerEvent(ServerPlayerEntity player){Map<String,Object> values=new HashMap<>();values.put("player",FabricPlatform.ref(player));values.put("sender",FabricPlatform.ref(player));values.put("event-player",FabricPlatform.ref(player));return values;}
    public static Map<String,Object> dispatch(String event,Map<String,Object> values){return active?ENGINE.dispatch(event,values):Map.of();}
    public static boolean command(ServerCommandSource source,String raw){
        if(!active)return false;String normalized=raw.replaceFirst("^/","");String[] parts=normalized.split(" +",2);Map<String,Object> event=new HashMap<>();event.put("sender",source.getEntity() instanceof ServerPlayerEntity p?FabricPlatform.ref(p):source);if(source.getEntity() instanceof ServerPlayerEntity p)event.put("player",FabricPlatform.ref(p));event.put("command",parts[0]);event.put("full command",normalized);event.put("block",new Values.Location(FabricPlatform.ref(source.getWorld()),source.getPosition().x,source.getPosition().y,source.getPosition().z,source.getRotation().y,source.getRotation().x));if(Boolean.TRUE.equals(dispatch("command",event).get("cancelled")))return true;
        for(var file:ENGINE.program().files.values())if(file.valid())for(var command:file.commands())if(command.name().equals(parts[0])||command.aliases().contains(parts[0])){int result=execute(parts[0],source,parts.length>1?parts[1]:"");source.getReturnValueConsumer().onResult(result>0,result);return true;}
        return false;
    }
    private static void reload(){
        if(!active)throw new IllegalStateException("Skript runtime is not active");
        try{
            Map<String,String> sources=new LinkedHashMap<>();try(var paths=Files.walk(ROOT.resolve("scripts"))){for(Path path:paths.filter(Files::isRegularFile).sorted().toList()){Path relative=ROOT.resolve("scripts").relativize(path);if(!path.toString().endsWith(".sk")||disabled(relative))continue;if(Files.size(path)>2_000_000)throw new IOException("Script is too large: "+relative);sources.put(relative.toString(),Files.readString(path));}}
            Program next=new Parser(PLATFORM).compile(sources);ENGINE.install(next);for(var file:next.files.values())if(file.valid())for(var command:file.commands()){register(command.name());for(String alias:command.aliases())register(alias);}
            installClassAdapter();writeReport();for(var file:next.files.values())for(var diagnostic:file.diagnostics())LOG.warn("{}: {}",diagnostic.source(),diagnostic.message());LOG.info("Native Skript: {}/{} source files active",validFiles(),next.files.size());
        }catch(IOException e){LOG.error("Skript reload failed; existing program kept",e);}
    }
    private static boolean disabled(Path path){for(Path part:path)if(part.toString().startsWith("-"))return true;return false;}
    private static void register(String name){
        if(!registeredCommands.add(name))return;var dispatcher=server.getCommandManager().getDispatcher();if(dispatcher.getRoot().getChild(name)!=null)LOG.info("Script source now handles existing command alias: {}",name);
        dispatcher.register(literal(name).executes(c->execute(name,c.getSource(),""))
            .then(argument("arguments",StringArgumentType.greedyString()).executes(c->execute(name,c.getSource(),StringArgumentType.getString(c,"arguments")))));
        for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())server.getCommandManager().sendCommandTree(player);
    }
    private static void installClassAdapter(){
        vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.nativeRuntime(new vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.NativeRuntime(){
            public boolean hasFunction(String name){return active&&ENGINE.program().functions.containsKey(name.toLowerCase(Locale.ROOT));}
            public Object invokeFunction(String name,List<Object> arguments){if(!active)throw new IllegalStateException("Skript runtime is inactive");return ENGINE.invokeFunction(name,arguments.stream().map(value->value instanceof net.minecraft.entity.Entity entity?FabricPlatform.ref(entity):value).toList());}
        });
        var source=ENGINE.program().files.values().stream().filter(Program.File::valid).filter(f->f.commands().stream().anyMatch(c->c.name().equals("svclass"))).findFirst().orElse(null);
        vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.classPolicy(source==null?null:new vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.ClassPolicy(){
            private final Program.Command command=source.commands().stream().filter(c->c.name().equals("svclass")).findFirst().orElseThrow();
            private final Map<String,Boolean> allowed=new HashMap<>();
            public boolean selectable(String id){return allowed.computeIfAbsent(id,key->{var compiler=new ExpressionCompiler(PLATFORM,ENGINE.program().functions.keySet(),source.options(),command.source());String quoted=key.toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("\"","\\\"");return Values.truth(ENGINE.evaluate(compiler.compile("svclass_valid(\""+quoted+"\")")));});}
            public boolean request(ServerPlayerEntity player,String id){return ENGINE.command(command,FabricPlatform.ref(player),List.of("switch",id.toLowerCase(Locale.ROOT)));}
            public Map<String,Object> details(ServerPlayerEntity player){
                var compiler=new ExpressionCompiler(PLATFORM,ENGINE.program().functions.keySet(),source.options(),command.source());
                double fee=Values.number(ENGINE.evaluate(compiler.compile("{@class-switch-fee}")));var duration=(Values.Span)ENGINE.evaluate(compiler.compile("{@class-switch-cooldown}"));
                Object last=ENGINE.variables().get("svclass.last::"+player.getUuid(),Map.of());long available=last instanceof Values.Date date?date.milliseconds()+(long)duration.milliseconds():0;
                return Map.of("fee",java.math.BigDecimal.valueOf(fee).stripTrailingZeros().toPlainString(),"cooldownSeconds",(long)(duration.milliseconds()/1000),"availableAt",available,"remainingSeconds",Math.max(0,(available-ENGINE.now()+999)/1000),"busy",Boolean.TRUE.equals(ENGINE.variables().get("svclass.lock::"+player.getUuid(),Map.of())));
            }
        });
        var command=ENGINE.program().files.values().stream().filter(Program.File::valid).flatMap(f->f.commands().stream()).filter(c->c.name().equals("svamk")).findFirst().orElse(null);
        vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime.scriptAdapter(command==null?null:new vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime.ScriptAdapter(){
            public boolean owns(String behavior){return behavior.startsWith("ANTI_MAGE_KNIGHT_");}
            public boolean canCast(String behavior,ServerPlayerEntity player){return active&&player.isAlive()&&!player.isSpectator();}
            public boolean cast(String behavior,ServerPlayerEntity player){String mode=switch(behavior){case "ANTI_MAGE_KNIGHT_PASSIVE"->"pulse";case "ANTI_MAGE_KNIGHT_NORTHERN_GUST"->"gust";case "ANTI_MAGE_KNIGHT_CLAP"->"clap";case "ANTI_MAGE_KNIGHT_HEROIC_SURGE"->"surge";case "ANTI_MAGE_KNIGHT_OVERLOAD"->"overload";case "ANTI_MAGE_KNIGHT_HIT_ME"->"hitme";case "ANTI_MAGE_KNIGHT_METEOR_LANDING"->"meteor";default->throw new IllegalArgumentException("Unknown Anti-Mage Knight behavior "+behavior);};return ENGINE.command(command,server.getCommandSource(),List.of(mode,FabricPlatform.ref(player)));}
        });
    }
    private static int execute(String name,ServerCommandSource sender,String raw){
        if(raw.isBlank()&&java.util.Set.of("classes","doiclass","svclass").contains(name)&&sender.getEntity() instanceof ServerPlayerEntity&&FabricLoader.getInstance().isModLoaded("fantasyhubcore")){server.getCommandManager().executeWithPrefix(sender,"fantasyhub classes");return 1;}

        for(var file:ENGINE.program().files.values())if(file.valid())for(var command:file.commands())if(command.name().equals(name)||command.aliases().contains(name)){
            List<String> parts=raw.isBlank()?List.of():List.of(raw.split(" +"));List<Object> arguments=new ArrayList<>();int index=0;
            for(int i=0;i<command.arguments().size()&&index<parts.size();i++){
                String type=command.arguments().get(i).type();String value=parts.get(index++);if(type.equals("text")&&i==command.arguments().size()-1){value=String.join(" ",parts.subList(index-1,parts.size()));index=parts.size();}
                try{Object argument=switch(type){case "number"->Double.parseDouble(value);case "integer"->(double)Long.parseLong(value);case "player"->{ServerPlayerEntity p=server.getPlayerManager().getPlayer(value);if(p==null)throw new IllegalArgumentException("Player is offline");yield FabricPlatform.ref(p);}default->value;};arguments.add(argument);}catch(IllegalArgumentException e){sender.sendError(Text.translatable("svframeskript.arguments"));return 0;}
            }
            if(index<parts.size()){sender.sendError(Text.translatable("svframeskript.arguments"));return 0;}
            Object subject=sender.getEntity() instanceof ServerPlayerEntity p?FabricPlatform.ref(p):sender;return ENGINE.command(command,subject,arguments)?1:0;
        }
        sender.sendError(Text.translatable("svframeskript.command.unavailable"));return 0;
    }
    private static long validFiles(){return ENGINE.program().files.values().stream().filter(Program.File::valid).count();}
    public static void runtimeError(Syntax.Source source,Throwable failure){if(failures.size()<1000)failures.add(Map.of("file",source.file(),"line",source.line(),"error",Objects.toString(failure.getMessage(),failure.getClass().getSimpleName())));}
    public static void trace(Syntax.Source source,String kind){counters.merge(source.file(),1L,Long::sum);}
    public static Map<String,Object> report(){Map<String,Object> out=new LinkedHashMap<>();out.put("format",1);out.put("active",active);out.put("sourceFiles",ENGINE.program().files.size());out.put("compiledFiles",validFiles());out.put("pendingTasks",ENGINE.pendingTasks());out.put("executedStatements",new LinkedHashMap<>(counters));out.put("runtimeErrors",List.copyOf(failures));List<Object> files=new ArrayList<>();for(var file:ENGINE.program().files.values()){Map<String,Object> f=new LinkedHashMap<>();f.put("file",file.name());f.put("compiled",file.valid());f.put("functions",file.functions().size());f.put("commands",file.commands().size());f.put("triggers",file.triggers().size());f.put("diagnostics",file.diagnostics().stream().map(d->Map.of("line",d.source().line(),"syntax",d.source().text(),"message",d.message())).toList());files.add(f);}out.put("files",files);return out;}
    private static void writeReport() throws IOException {Files.writeString(ROOT.resolve("compatibility.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report()));}
}
