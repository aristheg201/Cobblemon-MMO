package vn.svframe.fantasyhub.core;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.fantasyhub.protocol.Protocol;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.profess.resource.PlayerResource;
import vn.svframe.svframemmo.skill.PlayerSkillCatalog;
import vn.svframe.svframemmo.skill.ClassSkill;
import net.minecraft.registry.Registries;
import net.minecraft.item.Items;
import vn.svframe.svframemmo.runtime.PersistentHudRuntime;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static net.minecraft.server.command.CommandManager.literal;

/** Presentation bridge consuming the existing authoritative MMO owner. */
public final class FantasyHubCore implements ModInitializer {
    private static final Gson GSON=new Gson();
    private final Map<UUID,String> snapshots=new HashMap<>();
    private final Map<UUID,String> castingModes=new HashMap<>();
    private final Map<UUID,String> hudSnapshots=new HashMap<>();
    private final Map<UUID,Long> nextAction=new HashMap<>();
    private final Map<UUID,Long> nextObservation=new HashMap<>();
    private ExecutorService qaWriter;
    private long tick;
    @Override public void onInitialize(){
        Protocol.install();
        vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.registerMenu("svframe_class_switch",player->{snapshots.remove(player.getUuid());ServerPlayNetworking.send(player,new Protocol.Open("classes"));});
        vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.registerMenu("svframe_close",player->ServerPlayNetworking.send(player,new Protocol.Open("close")));
        ServerLifecycleEvents.SERVER_STARTING.register(server->{
            Path root=net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("SVFrameLib");
            try{
                boolean installedSource=SourcePackInstaller.install();
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
                if(!vn.svframe.svframelib.fabric.SVFrameLibFabricMod.reloadAll())throw new IllegalStateException("Cannot load authored source skill definitions");
                vn.svframe.svframelib.skill.NativeSkillProviders.reloadDefinitions();
                if(!SVFrameMMO.reload())throw new IllegalStateException("Cannot load authored class definitions");
                vn.svframe.svframelib.fabric.NativeVisualRuntime.reload();
                ClassSwitchService.reload();
                if(!vn.svframe.svframeitems.SVFrameItems.reload())throw new IllegalStateException("Cannot load source Dragonborn equipment definitions");
            }catch(java.io.IOException failure){throw new IllegalStateException("Cannot initialize Fantasy Hub presentation configuration",failure);}
        });
        PresentationHandshake.install();
        ServerPlayNetworking.registerGlobalReceiver(Protocol.Action.ID,(request,context)->{
            var player=context.player();long now=tick;
            if(now<nextAction.getOrDefault(player.getUuid(),-1L))return;
            nextAction.put(player.getUuid(),now+2);
            var data=SVFrameMMO.playerData().get(player);
            if(Boolean.TRUE.equals(ClassSwitchService.policy(data).get("busy"))&&!request.action().equals("refresh"))return;
            try{
                switch(request.action()){
                    case "switch_class" -> {ClassSwitchService.request(player,request.id(),()->snapshots.remove(player.getUuid()));return;}
                    case "allocate" -> {if(request.slot()!=1)return;data.spendAttributePoints(request.id(),1);}
                    case "respec" -> data.reallocateAttributes();
                    case "bind" -> {if(request.slot()<1||request.slot()>9)return;PlayerSkillCatalog.bind(data,request.slot(),request.id());}
                    case "unbind" -> {if(request.slot()<1||request.slot()>9)return;PlayerSkillCatalog.unbind(data,request.slot());}
                    case "upgrade" -> {var entry=PlayerSkillCatalog.owned(data,request.id());if(entry!=null)PlayerSkillCatalog.upgrade(data,entry,1);}
                    case "cast" -> {if(request.slot()<1||request.slot()>9)return;SVFrameMMO.skillRuntime().castBound(data,request.slot());}
                    case "cast_id" -> SVFrameMMO.skillRuntime().cast(data,request.id());
                    case "purchase" -> {var shop=vn.svframe.svframelib.skill.PlayerAbilityShop.get();if(shop==null)return;shop.purchaseUsing(player,request.id(),request.slot());}
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
            json.addProperty("serverVanillaLevel",context.player().experienceLevel);json.addProperty("serverVanillaProgress",context.player().experienceProgress);json.addProperty("serverVanillaTotal",context.player().totalExperience);
            json.addProperty("serverTick",tick);
            json.addProperty("scenario",RpgLifecycleQa.label(id));
            var writer=qaWriter;if(writer!=null&&!writer.isShutdown())writer.execute(()->{
                try{Path dir=Path.of("qa","fantasyhub");Files.createDirectories(dir);Files.writeString(dir.resolve(id+".jsonl"),GSON.toJson(json)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException e){java.util.logging.Logger.getLogger("FantasyHub-QA").log(java.util.logging.Level.WARNING,"Could not write QA observation",e);}
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{if("1".equals(System.getenv("SVFRAME_RUNTIME_QA")))qaWriter=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(512),r->{Thread t=new Thread(r,"FantasyHub-QA");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{snapshots.clear();castingModes.clear();hudSnapshots.clear();nextAction.clear();nextObservation.clear();if(qaWriter!=null)qaWriter.shutdown();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{if(qaWriter!=null)try{if(!qaWriter.awaitTermination(5,TimeUnit.SECONDS))qaWriter.shutdownNow();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->PersistentHudRuntime.setPresentationOwner(handler.player.getUuid(),true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{snapshots.remove(handler.player.getUuid());castingModes.remove(handler.player.getUuid());hudSnapshots.remove(handler.player.getUuid());nextAction.remove(handler.player.getUuid());nextObservation.remove(handler.player.getUuid());PersistentHudRuntime.setPresentationOwner(handler.player.getUuid(),false);});
        ServerTickEvents.END_SERVER_TICK.register(server->{
            ++tick;
            for(var player:server.getPlayerManager().getPlayerList()){
                if(!ServerPlayNetworking.canSend(player,Protocol.State.ID))continue;
                String mode=Objects.requireNonNullElse(SVFrameMMO.skillBar().mode(player.getUuid()),"");
                if(tick%5!=0&&mode.equals(castingModes.get(player.getUuid())))continue;
                castingModes.put(player.getUuid(),mode);
                String hud=GSON.toJson(hudState(player));
                if(ServerPlayNetworking.canSend(player,Protocol.Casting.ID)&&!hud.equals(hudSnapshots.put(player.getUuid(),hud)))ServerPlayNetworking.send(player,new Protocol.Casting(hud));
                String state=state(player);
                if(!state.equals(snapshots.put(player.getUuid(),state)))ServerPlayNetworking.send(player,new Protocol.State(state));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            RpgLifecycleQa.register(dispatcher);
            EconomyPolicyQa.register(dispatcher);
            for(String alias:List.of("classmenu","classes","doiclass"))dispatcher.register(literal(alias).executes(ctx->{
                var player=ctx.getSource().getPlayerOrThrow();snapshots.remove(player.getUuid());ServerPlayNetworking.send(player,new Protocol.Open("classes"));return 1;
            }));
            dispatcher.register(literal("svclass").then(literal("switch").then(net.minecraft.server.command.CommandManager.argument("class",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx->{
                var player=ctx.getSource().getPlayerOrThrow();String id=com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"class").toUpperCase(Locale.ROOT);
                return ClassSwitchService.request(player,id,()->snapshots.remove(player.getUuid()))?1:0;
            }))));
            dispatcher.register(literal("fantasyhub")
                .executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();snapshots.remove(player.getUuid());ServerPlayNetworking.send(player,new Protocol.Open("profile"));return 1;})
                .then(literal("classes").executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();ServerPlayNetworking.send(player,new Protocol.Open("classes"));return 1;}))
                .then(literal("classpurchase").requires(s->s.hasPermissionLevel(2))
                    .then(net.minecraft.server.command.CommandManager.argument("player",net.minecraft.command.argument.EntityArgumentType.player())
                    .then(net.minecraft.server.command.CommandManager.argument("class",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(ctx->{
                        var target=net.minecraft.command.argument.EntityArgumentType.getPlayer(ctx,"player");
                        return ClassSwitchService.request(target,com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,"class"),()->snapshots.remove(target.getUuid()))?1:0;
                    }))))
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
                .then(literal("hudstatus").executes(ctx->{
                    var player=ctx.getSource().getPlayerOrThrow();Map<String,Object> status=new LinkedHashMap<>();status.put("build",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fantasyhubcore").orElseThrow().getMetadata().getVersion().getFriendlyString());status.put("castingChannel",ServerPlayNetworking.canSend(player,Protocol.Casting.ID));status.put("casting",castingState(player));
                    ctx.getSource().sendFeedback(()->Text.literal(GSON.toJson(status)),false);return 1;
                }))
                .then(literal("resync").requires(s->s.hasPermissionLevel(2)).executes(ctx->{var player=ctx.getSource().getPlayerOrThrow();SVFrameMMO.playerData().get(player).refreshClassStats();snapshots.remove(player.getUuid());return 1;})));
        });
    }
    private static String state(ServerPlayerEntity player){
        var data=SVFrameMMO.playerData().get(player);Map<String,Object> out=new LinkedHashMap<>();
        out.put("schema",Protocol.VERSION);out.put("classId",data.getClassId());out.put("className",data.getProfess().getName());
        out.put("experienceDisplay",vn.svframe.svframemmo.experience.vanilla.VanillaProgressionRuntime.instance().experienceDisplayMode());
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
        out.put("classSwitch",ClassSwitchService.policy(data));
        List<Map<String,Object>> roster=new ArrayList<>();
        Map<vn.svframe.svframelib.api.economy.CurrencyKey,java.math.BigDecimal> classBalances=new HashMap<>();
        for(var type:SVFrameMMO.classes().getAll()) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",type.getId());row.put("name",type.getName());
            row.put("description",type.getDescription());row.put("attributeDescription",type.getAttributeDescription());row.put("maxLevel",type.getMaxLevel());
            row.put("price",ClassSwitchService.offer(type.getId(),player,classBalances));row.put("selectable",ClassSwitchService.selectable(type.getId()));row.put("unlocked",ClassSwitchService.unlocked(player,type.getId()));
            row.put("current",data.getClassId().equals(type.getId()));
            Map<String,Double> baseStats=new TreeMap<>();for(String stat:type.getStats())baseStats.put(stat,type.calculateBaseStat(stat,1,data));row.put("baseStats",baseStats);
            List<Map<String,Object>> classSkills=new ArrayList<>();
            for(var skill:type.getConfiguredSkills()) {
                Map<String,Object> sr=new LinkedHashMap<>();sr.put("id",skill.getSkill().getId());sr.put("name",skill.getSkill().getName());
                sr.put("passive",skill.getTrigger().isPassive());sr.put("requiredLevel",skill.getUnlockLevel());skillSupport(sr,skill.getSkill());classSkills.add(sr);
            }
            row.put("skills",classSkills);roster.add(row);
        }
        out.put("classRoster",roster);
        out.put("classPreviews",ClassEquipmentPreviews.snapshots(player));
        List<Map<String,Object>> skills=new ArrayList<>();
        for(var entry:PlayerSkillCatalog.entries(data)){
            var skill=entry.skill();Map<String,Object> row=new LinkedHashMap<>();
            skillSupport(row,skill.getSkill());
            row.put("id",entry.id());row.put("name",skill.getSkill().getName());row.put("owned",entry.learned());row.put("level",entry.level());row.put("bindable",entry.bindable());
            row.put("slots",PlayerSkillCatalog.allowedSlots(data,entry));
            row.put("passive",skill.getTrigger().isPassive());skills.add(row);
            row.put("requiredLevel",skill.getUnlockLevel());row.put("maxLevel",skill.hasMaxLevel()?skill.getMaxLevel():0);row.put("upgradable",skill.isUpgradable());
            int level=Math.max(1,entry.level());
            Map<String,Double> parameters=new TreeMap<>();skill.getParameters().keySet().forEach(key->parameters.put(key,skill.getParameter(key,level,data)));
            row.put("parameters",parameters);row.put("cooldownRemaining",MMOPlayerData.setup(player).getCooldownMap().getCooldown(skill.getCooldownPath()));
            row.put("lore",skill.getSkill().getUiLore());
            row.put("iconMaterial",net.minecraft.registry.Registries.ITEM.getId(skill.getSkill().getIcon().getMaterialElse(net.minecraft.item.Items.PAPER)).toString());
            row.put("iconCustomModelData",skill.getSkill().getIcon().getCustomModelDataInt());
        }
        out.put("skills",skills);
        out.put("loadoutSlots",PlayerSkillCatalog.slots(data));
        out.put("casting",castingState(player));
        var shop=vn.svframe.svframelib.skill.PlayerAbilityShop.get();out.put("pokemonShop",shop==null?List.of():shop.catalog(player));
        Map<Integer,String> loadout=new TreeMap<>();PlayerSkillCatalog.bindings(data).forEach((slot,entry)->loadout.put(slot,entry.id()));out.put("loadout",loadout);
        Map<String,Double> stats=new TreeMap<>();for(String id:List.of("MAX_HEALTH","ARMOR","ARMOR_TOUGHNESS","ATTACK_DAMAGE","SKILL_DAMAGE","MOVEMENT_SPEED"))stats.put(id,MMOPlayerData.setup(player).getStatMap().getStat(id));out.put("stats",stats);
        return GSON.toJson(out);
    }
    private static Map<String,Object> castingState(ServerPlayerEntity player){
        var data=SVFrameMMO.playerData().get(player);
      Map<String, Object> casting = new LinkedHashMap<>();
      casting.put("active", SVFrameMMO.skillBar().isCasting(player.getUuid()));
      casting.put("mode", Objects.requireNonNullElse(SVFrameMMO.skillBar().mode(player.getUuid()), ""));
      List<Map<String, Object>> castingSlots = new ArrayList<>();

      for (vn.svframe.svframemmo.skill.runtime.SkillBarRuntime.SkillBarBinding binding : SVFrameMMO.skillBar().skillBarBindings(data)) {
         ClassSkill skill = binding.skill();
         Map<String, Object> row = new LinkedHashMap<>();
         row.put("boundSlot", binding.boundSlot());
         row.put("inputSlot", binding.inputSlot());
         row.put("temporary", binding.temporary());
         row.put("id", skill.getSkill().getId());
         row.put("name", skill.getSkill().getName());
         int level = PlayerSkillCatalog.level(data, skill);
         Map<String, Double> parameters = new TreeMap<>();
         skill.getParameters().keySet().forEach(key -> parameters.put(key, skill.getParameter(key, level, data)));
         row.put("parameters", parameters);
         row.put("cooldownRemaining", data.getMMOPlayerData().getCooldownMap().getCooldown(skill.getCooldownPath()));
         row.put("iconMaterial", Registries.ITEM.getId(skill.getSkill().getIcon().getMaterialElse(Items.PAPER)).toString());
         row.put("iconCustomModelData", skill.getSkill().getIcon().getCustomModelDataInt());
         castingSlots.add(row);
      }

      casting.put("slots", castingSlots);
      return casting;
    }
    private static Map<String,Object> hudState(ServerPlayerEntity player){
        var data=SVFrameMMO.playerData().get(player);Map<String,Object> out=new LinkedHashMap<>();
        out.put("schema",Protocol.VERSION);out.put("casting",castingState(player));
        out.put("experienceDisplay",vn.svframe.svframemmo.experience.vanilla.VanillaProgressionRuntime.instance().experienceDisplayMode());
        out.put("level",data.getLevel());out.put("experience",data.getExperience());out.put("nextLevel",data.getProfess().getExpCurve().getExperience(data,data.getLevel()));
        for(var resource:PlayerResource.values()){
            String name=resource==PlayerResource.STELLIUM?"magic":resource.name().toLowerCase(Locale.ROOT);
            out.put(name,data.getResource(resource));out.put(name+"Max",data.getMaxResource(resource));
        }
        return out;
    }
    private static void skillSupport(Map<String,Object> row,vn.svframe.svframelib.skill.handler.SkillHandler<?> handler) {
        if(handler instanceof vn.svframe.svframelib.skill.NativeSkillProviders.BackedHandler backed) {
            boolean available=vn.svframe.svframelib.skill.NativeSkillProviders.available(backed.providerNamespace(),backed.sourceSkill());
            row.put("available",available);row.put("issues",available?List.of():vn.svframe.svframelib.skill.NativeSkillProviders.issues(backed.providerNamespace(),backed.sourceSkill()));
        } else {row.put("available",true);row.put("issues",List.of());}
    }

}
