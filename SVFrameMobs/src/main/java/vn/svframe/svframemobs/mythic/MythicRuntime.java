package vn.svframe.svframemobs.mythic;

import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import org.slf4j.LoggerFactory;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.skill.NativeSkillProviders;
import vn.svframe.svframelib.skill.handler.SkillHandlerSource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static vn.svframe.svframemobs.mythic.MythicEngine.*;

public final class MythicRuntime implements NativeSkillProviders.Provider {
    private static final MythicRuntime INSTANCE=new MythicRuntime();
    private final FabricMythicPlatform platform=new FabricMythicPlatform();
    private final MythicEngine engine=new MythicEngine(platform);
    private final Path root=FabricLoader.getInstance().getConfigDir().resolve("SVFrameMobs");
    private Map<String,MythicDefinition> inline=Map.of();
    private boolean installed;
    private boolean definitionsLoaded;
    private MythicRuntime(){}
    public static MythicRuntime instance(){return INSTANCE;}
    public void install() {
        if(installed)return;installed=true;
        SVFrameLib.bootstrap();SVFrameLib.plugin.getSkills().registerSkillHandlerSource(new SkillHandlerSource("mythicmobs",MythicSkillHandler::new,List.of("mythicmobs-skill")));
        NativeSkillProviders.register("mythicmobs",this);
        ServerLifecycleEvents.SERVER_STARTING.register(server->{platform.server(server);try{if(!definitionsLoaded)reload();}catch(IOException failure){throw new IllegalStateException("Cannot load native Mythic graphs",failure);}});
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount)->!platform.blocksDamage(entity,source));
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,baseDamage,damageTaken,blocked)->{if(damageTaken>0)platform.damaged(entity);});
        ServerTickEvents.END_SERVER_TICK.register(server->{platform.tick(engine.currentTick()+1);engine.tick();});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{engine.clear();platform.clear();definitionsLoaded=false;});
    }
    public void reload() throws IOException {
        Path folder=root.resolve("skills");Files.createDirectories(folder);Map<String,MythicDefinition> parsed=new LinkedHashMap<>();Map<String,List<String>> failures=new LinkedHashMap<>();
        try(var files=Files.walk(folder)) {
            for(Path file:files.filter(path->Files.isRegularFile(path)&&(path.toString().endsWith(".yml")||path.toString().endsWith(".yaml"))).sorted().toList()) {
                if(Files.size(file)>16_000_000)throw new IllegalArgumentException("Meta-skill file exceeds size limit: "+file);
                for(var row:YamlLite.map(YamlLite.parse(file)).entrySet()) {
                    if(parsed.containsKey(row.getKey())||failures.containsKey(row.getKey()))throw new IllegalArgumentException("Duplicate native Mythic ID "+row.getKey()+" in "+file);
                    try {parsed.put(row.getKey(),MythicDefinition.parse(row.getKey(),root.relativize(file).toString(),YamlLite.map(row.getValue())));}
                    catch(IllegalArgumentException error){failures.put(row.getKey(),List.of(file.getFileName()+": "+error.getMessage()));}
                }
            }
        }
        parsed.putAll(inline);engine.replace(parsed,failures);
        definitionsLoaded=true;
        // Compilation diagnostics are materialized during reload, never each tick.
        Path report=root.resolve("compatibility.json");String json=new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("definitions",parsed.size(),"available",parsed.keySet().stream().filter(engine::available).count(),"unavailable",engine.failures()));
        Path temporary=Files.createTempFile(root,"compatibility-",".tmp");Files.writeString(temporary,json);
        try{Files.move(temporary,report,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,report,StandardCopyOption.REPLACE_EXISTING);}
        LoggerFactory.getLogger("SVFrameMobs").info("Native Mythic graphs: {} definitions, {} executable, {} unavailable",parsed.size(),parsed.keySet().stream().filter(engine::available).count(),engine.failures().size());
    }
    @Override public void reloadDefinitions() throws IOException {reload();}
    public void inline(Map<String,MythicDefinition> definitions) throws IOException {inline=Map.copyOf(definitions);reload();}
    @Override public boolean available(String id){return engine.available(id);}
    @Override public void materialize(vn.svframe.svframelib.manager.SkillManager manager) {
        Path folder=FabricLoader.getInstance().getConfigDir().resolve("SVFrameLib/skill");
        if(!Files.isDirectory(folder))return;
        try(var paths=Files.walk(folder)) {
            Map<String,MythicSkillHandler> next=new LinkedHashMap<>();
            for(Path file:paths.filter(p->Files.isRegularFile(p)&&(p.toString().endsWith(".yml")||p.toString().endsWith(".yaml"))).sorted().toList()) {
                for(var row:YamlLite.map(YamlLite.parse(file)).entrySet()) {
                    Map<String,Object> raw=YamlLite.map(row.getValue());String source=String.valueOf(raw.getOrDefault("source",""));
                    if(!source.startsWith("mythicmobs:"))continue;
                    var current=manager.getHandler(row.getKey());if(current!=null&&!(current instanceof MythicSkillHandler))continue;
                    var handler=new MythicSkillHandler(new vn.svframe.svframelib.util.configobject.MapConfigObject(row.getKey(),raw),source.substring(11));
                    if(next.putIfAbsent(row.getKey(),handler)!=null)throw new IllegalArgumentException("Duplicate Mythic wrapper "+row.getKey());
                }
            }
            next.values().forEach(manager::replaceSkillHandler);
        }catch(IOException failure){throw new IllegalStateException("Cannot materialize native Mythic skill wrappers",failure);}
    }
    @Override public List<String> issues(String id){return available(id)?List.of():engine.failures().getOrDefault(id,List.of("Missing meta-skill "+id));}
    @Override public boolean cast(String id,UUID caster,UUID target,Map<String,?> parameters){Prepared prepared=prepare(id,caster,target,parameters);return prepared!=null&&start(prepared);}
    public Prepared prepare(String id,UUID caster,UUID target,Map<String,?> parameters){
        Entity actor=platform.entity(caster);if(actor==null)return null;if(target==null&&actor instanceof ServerPlayerEntity player)target=lookTarget(player);
        return engine.prepare(id,caster,null,new Target(target==null?caster:target,null),FabricMythicPlatform.point(actor),parameters);
    }
    public boolean start(Prepared prepared){return engine.start(prepared);}
    public boolean event(String id,Entity caster,Entity trigger,boolean death){return engine.cast(id,caster.getUuid(),trigger==null?null:trigger.getUuid(),new Target(trigger==null?caster.getUuid():trigger.getUuid(),null),FabricMythicPlatform.point(caster),death?Map.of("__death_trigger",true):Map.of());}
    public void combat(Entity actor){platform.markCombat(actor);}
    public int count(){return engine.count();}
    public MythicEngine engine(){return engine;}
    public FabricMythicPlatform platform(){return platform;}
    private UUID lookTarget(ServerPlayerEntity player) {
        ServerWorld world=player.getServerWorld();Vec3d from=player.getEyePos(),end=from.add(player.getRotationVec(1).multiply(32));
        HitResult block=world.raycast(new RaycastContext(from,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));if(block.getType()!=HitResult.Type.MISS)end=block.getPos();
        Vec3d to=end;LivingEntity best=null;double nearest=from.squaredDistanceTo(to);
        for(LivingEntity candidate:world.getEntitiesByClass(LivingEntity.class,new Box(from,to).expand(1),e->e!=player&&e.isAlive()&&!e.isSpectator())) {
            var hit=candidate.getBoundingBox().expand(.3).raycast(from,to);if(hit.isEmpty())continue;double distance=from.squaredDistanceTo(hit.get());if(distance<nearest){best=candidate;nearest=distance;}
        }
        return best==null?null:best.getUuid();
    }
}
