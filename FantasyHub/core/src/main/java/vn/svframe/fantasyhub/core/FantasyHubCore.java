package vn.svframe.fantasyhub.core;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.fantasyhub.protocol.Protocol;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;
import vn.svframe.svframemmo.runtime.PersistentHudRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Presentation bridge consuming the existing authoritative MMO owner. */
public final class FantasyHubCore implements ModInitializer {
    private static final Gson GSON=new Gson();
    private final Map<UUID,String> snapshots=new HashMap<>();
    private final Map<UUID,Long> nextAction=new HashMap<>();
    private final Map<UUID,Long> nextObservation=new HashMap<>();
    private ExecutorService qaWriter;
    private long tick;
    @Override public void onInitialize(){
        Protocol.install();
        ServerLifecycleEvents.SERVER_STARTING.register(server->{
            Path root=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("SVFrameLib");
            try{
                for(String name:List.of("reference-models.json","visual-anchors.json")){
                    Path target=name.equals("reference-models.json")?root.resolve("visual-models").resolve(name):root.resolve(name);
                    if(Files.exists(target))continue;
                    try(var input=getClass().getResourceAsStream("/default/presentation/"+name)){
                        if(input==null)throw new IllegalStateException("Fantasy Hub server artifact is missing presentation configuration: "+name);
                        Files.createDirectories(target.getParent());Files.copy(input,target);
                    }
                }
                boolean installedClass=false;
                Path classes=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("SVFrameMMO/classes");
                for(String name:List.of("death_knight.yml","anti_mage_knight.yml")){
                    Path target=classes.resolve(name);if(Files.exists(target))continue;
                    try(var input=getClass().getResourceAsStream("/default/presentation/classes/"+name)){
                        if(input==null)throw new IllegalStateException("Missing reference class configuration: "+name);
                        Files.createDirectories(classes);Files.copy(input,target);installedClass=true;
                    }
                }
                // Definitions must exist before saved characters attach at SERVER_STARTED.
                if(installedClass&&!SVFrameMMO.reload())throw new IllegalStateException("Cannot load newly installed reference class definitions");
                vn.svframe.svframelib.fabric.NativeVisualRuntime.reload();
            }catch(java.io.IOException failure){throw new IllegalStateException("Cannot initialize Fantasy Hub presentation configuration",failure);}
        });
        ServerLoginNetworking.registerGlobalReceiver(Protocol.LOGIN,(server,handler,understood,buf,sync,sender)->{
            boolean valid=false;
            try{valid=understood&&buf.readVarInt()==Protocol.VERSION&&Protocol.ASSET_REVISION.equals(buf.readString(128))&&buf.readableBytes()==0;}catch(RuntimeException ignored){}
            if(!valid)handler.disconnect(Text.translatableWithFallback("fantasyhub.connection.incompatible", "Fantasy Hub requires FantasyHubClient 0.1.0 for Minecraft 1.21.1. Install the matching client JAR and reconnect."));
        });
        ServerLoginConnectionEvents.QUERY_START.register((handler,server,sender,sync)->{
            PacketByteBuf buf=new PacketByteBuf(Unpooled.buffer());buf.writeVarInt(Protocol.VERSION);buf.writeString(Protocol.ASSET_REVISION,128);
            sender.sendPacket(Protocol.LOGIN,buf);
        });
        ServerPlayNetworking.registerGlobalReceiver(Protocol.Action.ID,(request,context)->{
            var player=context.player();long now=tick;
            if(now<nextAction.getOrDefault(player.getUuid(),-1L))return;
            nextAction.put(player.getUuid(),now+2);
            var data=SVFrameMMO.playerData().get(player);
            try{
                switch(request.action()){
                    case "allocate" -> {if(request.slot()!=1)return;data.spendAttributePoints(request.id(),1);}
                    case "respec" -> data.reallocateAttributes();
                    case "bind" -> {if(request.slot()<1||request.slot()>9)return;PlayerSkillCatalog.bind(data,request.slot(),request.id());}
                    case "unbind" -> {if(request.slot()<1||request.slot()>9)return;PlayerSkillCatalog.unbind(data,request.slot());}
                    case "upgrade" -> {var entry=PlayerSkillCatalog.definition(data,request.id());if(entry!=null)PlayerSkillCatalog.upgrade(data,entry,1);}
                    case "cast" -> {if(request.slot()<1||request.slot()>9)return;SVFrameMMO.skillRuntime().castBound(data,request.slot());}
                    case "cast_id" -> SVFrameMMO.skillRuntime().cast(data,request.id());
                    case "purchase" -> {var shop=vn.svframe.svframelib.skill.PlayerAbilityShop.get();if(shop==null)return;shop.purchase(player,request.id());}
                    case "refresh" -> snapshots.remove(player.getUuid());
                    default -> {return;}
                }
                if(!Set.of("cast","cast_id","purchase","refresh").contains(request.action()))SVFrameMMO.playerData().savePlayer(data);
                snapshots.remove(player.getUuid());
            }catch(IllegalArgumentException|IllegalStateException rejected){player.sendMessage(Text.translatable("fantasyhub.action.rejected"),true);}
        });
        ServerPlayNetworking.registerGlobalReceiver(Protocol.Observation.ID,(observation,context)->{
            if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))return;
            UUID id=context.player().getUuid();
            if(tick<nextObservation.getOrDefault(id,-1L))return;
            nextObservation.put(id,tick+2);
            com.google.gson.JsonObject json;
            try{json=JsonParser.parseString(observation.json()).getAsJsonObject();}catch(RuntimeException malformed){return;}
            json.addProperty("serverMaxHealth",context.player().getMaxHealth());json.addProperty("serverHealth",context.player().getHealth());
            json.addProperty("serverTick",tick);
            json.addProperty("scenario",RpgLifecycleQa.label(id));
            var writer=qaWriter;if(writer!=null&&!writer.isShutdown())writer.execute(()->{
                try{Path dir=Path.of("qa","fantasyhub");Files.createDirectories(dir);Files.writeString(dir.resolve(id+".jsonl"),GSON.toJson(json)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException e){java.util.logging.Logger.getLogger("FantasyHub-QA").log(java.util.logging.Level.WARNING,"Could not write QA observation",e);}
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if("1".equals(System.getenv("SVFRAME_RUNTIME_QA")))qaWriter=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(512),r->{Thread t=new Thread(r,"FantasyHub-QA");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{snapshots.clear();nextAction.clear();nextObservation.clear();if(qaWriter!=null)qaWriter.shutdown();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{if(qaWriter!=null)try{if(!qaWriter.awaitTermination(5,TimeUnit.SECONDS))qaWriter.shutdownNow();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->PersistentHudRuntime.setPresentationOwner(handler.player.getUuid(),true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{snapshots.remove(handler.player.getUuid());nextAction.remove(handler.player.getUuid());nextObservation.remove(handler.player.getUuid());PersistentHudRuntime.setPresentationOwner(handler.player.getUuid(),false);});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(++tick%5!=0)return;
            for(var player:server.getPlayerManager().getPlayerList()){
                if(!ServerPlayNetworking.canSend(player,Protocol.State.ID))continue;
                String state=state(player);
                if(!state.equals(snapshots.put(player.getUuid(),state)))ServerPlayNetworking.send(player,new Protocol.State(state));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            RpgLifecycleQa.register(dispatcher);
            dispatcher.register(literal("fantasyhub")
                .executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();snapshots.remove(player.getUuid());ServerPlayNetworking.send(player,new Protocol.Open("profile"));return 1;})
                .then(literal("shop").executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();ServerPlayNetworking.send(player,new Protocol.Open("pokemon_shop"));return 1;}))
                .then(literal("attributes").executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();ServerPlayNetworking.send(player,new Protocol.Open("attributes"));return 1;}))
                .then(literal("skills").executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();ServerPlayNetworking.send(player,new Protocol.Open("skills"));return 1;}))
                .then(literal("inspect").requires(s->s.hasPermissionLevel(2))
                    .then(net.minecraft.server.command.CommandManager.argument("player",net.minecraft.command.argument.EntityArgumentType.player()).executes(ctx->{
                        var player=net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx,"player");
                        var snapshot=JsonParser.parseString(state(player)).getAsJsonObject();snapshot.remove("pokemonShop");
                        for(var row:snapshot.getAsJsonArray("skills"))row.getAsJsonObject().remove("parameters");
                        ctx.getSource().sendFeedback(()->Text.literal(snapshot.toString()),false);return 1;
                    })))
                .then(literal("resync").requires(s->s.hasPermissionLevel(2)).executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();SVFrameMMO.playerData().get(player).refreshClassStats();snapshots.remove(player.getUuid());return 1;})));
        });
    }
    private static String state(ServerPlayerEntity player){
        var data=SVFrameMMO.playerData().get(player);Map<String,Object> out=new LinkedHashMap<>();
        out.put("schema",Protocol.VERSION);out.put("classId",data.getClassId());out.put("className",data.getProfess().getName());
        if("1".equals(System.getenv("SVFRAME_RUNTIME_QA")))out.put("qaScenario",RpgLifecycleQa.label(player.getUuid()));
        out.put("level",data.getLevel());out.put("experience",data.getExperience());out.put("nextLevel",data.getProfess().getExpCurve().getExperience(data,data.getLevel()));
        out.put("attributePoints",data.getAttributePoints());out.put("skillPoints",data.getSkillPoints());out.put("respecPoints",data.getAttributeReallocationPoints());
        for(var resource:PlayerResource.values()){
            String name=resource==PlayerResource.STELLIUM?"magic":resource.name().toLowerCase(Locale.ROOT);
            out.put(name,data.getResource(resource));out.put(name+"Max",data.getMaxResource(resource));
        }
        List<Map<String,Object>> attributes=new ArrayList<>();
        for(var attr:SVFrameMMO.attributes().getAll()){
            var instance=data.getAttributes().getInstance(attr);Map<String,Object> row=new LinkedHashMap<>();
            row.put("id",attr.getId());row.put("name",attr.getName());row.put("base",instance.getBase());row.put("total",instance.getTotal());row.put("max",attr.hasMax()?attr.getMax():-1);attributes.add(row);
        }
        out.put("attributes",attributes);
        List<Map<String,Object>> skills=new ArrayList<>();
        for(var entry:PlayerSkillCatalog.entries(data)){
            var skill=entry.skill();Map<String,Object> row=new LinkedHashMap<>();
            row.put("id",entry.id());row.put("name",skill.getSkill().getName());row.put("owned",entry.learned());row.put("level",entry.level());row.put("bindable",entry.bindable());
            row.put("slots",PlayerSkillCatalog.allowedSlots(data,entry));
            row.put("passive",skill.getTrigger().isPassive());skills.add(row);
            row.put("requiredLevel",skill.getUnlockLevel());row.put("maxLevel",skill.hasMaxLevel()?skill.getMaxLevel():0);row.put("upgradable",skill.isUpgradable());
            int level=Math.max(1,entry.level());
            Map<String,Double> parameters=new TreeMap<>();skill.getParameters().keySet().forEach(key->parameters.put(key,skill.getParameter(key,level,data)));
            row.put("parameters",parameters);row.put("cooldownRemaining",MMOPlayerData.setup(player).getCooldownMap().getCooldown(skill.getCooldownPath()));
        }
        out.put("skills",skills);
        out.put("loadoutSlots",PlayerSkillCatalog.slots(data));
        var shop=vn.svframe.svframelib.skill.PlayerAbilityShop.get();out.put("pokemonShop",shop==null?List.of():shop.catalog(player));
        Map<Integer,String> loadout=new TreeMap<>();PlayerSkillCatalog.bindings(data).forEach((slot,entry)->loadout.put(slot,entry.id()));out.put("loadout",loadout);
        Map<String,Double> stats=new TreeMap<>();for(String id:List.of("MAX_HEALTH","ARMOR","ARMOR_TOUGHNESS","ATTACK_DAMAGE","SKILL_DAMAGE","MOVEMENT_SPEED"))stats.put(id,MMOPlayerData.setup(player).getStatMap().getStat(id));out.put("stats",stats);
        return GSON.toJson(out);
    }
}
