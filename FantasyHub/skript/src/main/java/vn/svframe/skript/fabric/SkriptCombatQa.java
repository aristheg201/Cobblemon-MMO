package vn.svframe.skript.fabric;

import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.skript.lang.Values;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.persistence.PlayerDataSnapshot;

/** Opt-in conformance observations using the original loaded AMK source and real native damage. */
final class SkriptCombatQa {
    private static final Map<UUID,Session> sessions=new HashMap<>();
    static void disconnected(ServerPlayerEntity player){Session session=sessions.remove(player.getUuid());if(session!=null)restore(player,session);}
    private static final class Session {
        final PlayerDataSnapshot profile;
        final float health;
        final Map<String,Object> variables;
        final Map<String,Object> results=new LinkedHashMap<>();
        final Set<UUID> actors=new HashSet<>();
        final List<Entity> spawned=new ArrayList<>();
        Session(ServerPlayerEntity player){profile=PlayerDataSnapshot.capture(SVFrameMMO.playerData().get(player));health=player.getHealth();variables=SVFrameSkript.ENGINE.variables().snapshot();actors.add(player.getUuid());}
    }
    static int run(ServerPlayerEntity player,String stage)throws java.io.IOException {
        if(!"1".equals(System.getenv("SVFRAME_RUNTIME_QA")))throw new IllegalStateException("Runtime QA is disabled");
        if(stage.equals("combat_begin")){
            if(sessions.containsKey(player.getUuid()))throw new IllegalStateException("Finish the previous combat QA session first");
            Session session=new Session(player);sessions.put(player.getUuid(),session);
            var data=SVFrameMMO.playerData().get(player);data.setClassId("ANTI_MAGE_KNIGHT");
            clear(player);player.setHealth(player.getMaxHealth());
            session.results.put("sourceFiles",SVFrameSkript.report().get("files"));session.results.put("maxHealth",player.getMaxHealth());
            player.sendMessage(Text.literal("Original AMK combat QA started; use combat_finish to restore the test character."),false);return 1;
        }
        Session session=sessions.get(player.getUuid());if(session==null)throw new IllegalStateException("Run combat_begin first");
        switch(stage){
            case "combat_shield"->{
                clear(player);ready(player);float before=player.getHealth();boolean applied=player.damage(player.getDamageSources().generic(),4);
                double expected=player.getMaxHealth()*.16-2;Object left=get("shield.amount",player);
                session.results.put("shieldCancelsNativeDamage",!applied&&player.getHealth()==before&&player.hurtTime==0);
                session.results.put("shieldSourceUnits",left instanceof Number n&&Math.abs(n.doubleValue()-expected)<1e-5);
                session.results.put("shieldRemaining",left);
                ready(player);float overflow=(float)(Math.max(0,expected)+1)*2;player.damage(player.getDamageSources().generic(),overflow);
                session.results.put("shieldBreaksAndRemovesVariable",get("shield.amount",player)==null&&player.getHealth()<before);
                session.results.put("shieldLastProcPreserved",get("shield.last",player) instanceof Values.Date);
            }
            case "combat_guard"->{
                clear(player);ready(player);set("shield.last",player,new Values.Date(SVFrameSkript.ENGINE.now()));
                set("hitme",player,true);set("hitme.stored",player,0d);set("hitme.ticks",player,120d);
                float before=player.getHealth();boolean applied=player.damage(player.getDamageSources().generic(),10);
                double stored=number(get("hitme.stored",player));
                session.results.put("guardStoresUnreducedSourceDamage",Math.abs(stored-5)<1e-5);
                session.results.put("guardReducesRealDamage",applied&&player.getHealth()<before&&before-player.getHealth()<10);
                session.results.put("guardActualLoss",before-player.getHealth());session.results.put("guardStored",stored);
            }
            case "combat_taunt_begin"->{
                ServerPlayerEntity peer=peer(player);session.actors.add(peer.getUuid());clear(player);clear(peer);ready(player);
                // Isolate guard/redirection from the separately verified thirty-second shield proc.
                set("shield.last",player,new Values.Date(SVFrameSkript.ENGINE.now()));
                SVFrameSkript.command(player.getServer().getCommandSource(),"svamk overload "+player.getName().getString());
                session.results.put("tauntStartTick",player.getServer().getTicks());session.results.put("peer",peer.getName().getString());
                CowEntity cow=EntityType.COW.create(player.getServerWorld());if(cow==null)throw new IllegalStateException("Cow creation failed");
                cow.setAiDisabled(true);cow.refreshPositionAndAngles(player.getX()+2,player.getY(),player.getZ(),0,0);player.getServerWorld().spawnEntity(cow);session.spawned.add(cow);
            }
            case "combat_taunt_hit"->{
                ServerPlayerEntity peer=peer(player);CowEntity cow=cow(session);if(!Boolean.TRUE.equals(get("hitme",player))||get("taunt.owner",peer)==null)throw new IllegalStateException("Wait for the original taunt tick and keep both players within ten blocks");
                float victimBefore=cow.getHealth(),ownerBefore=player.getHealth();double storedBefore=number(get("hitme.stored",player));
                boolean applied=cow.damage(cow.getDamageSources().playerAttack(peer),10);
                session.results.put("tauntUsesOriginalPeriodicOwner",get("taunt.owner",peer).equals(FabricPlatform.ref(player)));
                session.results.put("tauntCancelsOriginalVictimDamage",!applied&&cow.getHealth()==victimBefore);
                session.results.put("tauntRedirectsToKnight",player.getHealth()<ownerBefore);
                session.results.put("tauntStoresDamageOnce",Math.abs(number(get("hitme.stored",player))-storedBefore-5)<1e-5);
                session.results.put("tauntActualRedirectLoss",ownerBefore-player.getHealth());session.results.put("tauntVictimBefore",victimBefore);
            }
            case "combat_taunt_expired"->{
                ServerPlayerEntity peer=peer(player);CowEntity cow=cow(session);
                session.results.put("tauntExpiresOnOriginalTimer",get("hitme",player)==null&&get("taunt.owner",peer)==null&&get("hitme.stored",player)==null);
                session.results.put("guardReleaseHitsRealEntity",cow.getHealth()<number(session.results.get("tauntVictimBefore")));
                session.results.put("tauntEndTick",player.getServer().getTicks());session.results.put("releaseCowHealth",cow.getHealth());
            }
            case "combat_pvp_protection"->{
                ServerPlayerEntity peer=peer(player);ready(player);float before=player.getHealth();boolean pvp=player.getServer().isPvpEnabled();
                try{player.getServer().setPvpEnabled(false);sourceDamage(peer,player,4);session.results.put("sourcePlayerDamageHonorsPvpOff",player.getHealth()==before);}
                finally{player.getServer().setPvpEnabled(pvp);}
            }
            case "combat_pokemon"->{
                LivingEntity target=player.getServerWorld().getEntitiesByClass(LivingEntity.class,player.getBoundingBox().expand(8),e->net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString().equals("cobblemon:pokemon")&&e.isAlive()).stream().min(Comparator.comparingDouble(e->e.squaredDistanceTo(player))).orElseThrow(()->new IllegalStateException("Place a real Cobblemon entity within eight blocks"));
                float before=target.getHealth();boolean allowed=vn.svframe.svframelib.entity.RpgEntityAdapters.allows(player,target,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.DAMAGE);
                sourceDamage(player,target,4);
                String mode=allowed?"wild":"protected";session.results.put(mode+"PokemonSourceDamage",allowed?target.getHealth()<before:target.getHealth()==before);
                session.results.put(mode+"PokemonBefore",before);session.results.put(mode+"PokemonAfter",target.getHealth());
            }
            case "combat_finish"->{
                try{
                    var required=List.of("shieldCancelsNativeDamage","shieldSourceUnits","shieldBreaksAndRemovesVariable","shieldLastProcPreserved","guardStoresUnreducedSourceDamage","guardReducesRealDamage","sourcePlayerDamageHonorsPvpOff","tauntUsesOriginalPeriodicOwner","tauntCancelsOriginalVictimDamage","tauntRedirectsToKnight","tauntStoresDamageOnce","tauntExpiresOnOriginalTimer","guardReleaseHitsRealEntity");
                    boolean passed=required.stream().allMatch(key->Boolean.TRUE.equals(session.results.get(key)));
                    session.results.put("runtimeVerdict",passed?"PASS":"FAIL");save(player,session);
                    if(!passed)throw new IllegalStateException("Original AMK combat conformance failed or a required stage was not executed");
                }
                finally{restore(player,session);sessions.remove(player.getUuid());}
            }
            default->throw new IllegalArgumentException("Unknown AMK combat QA stage");
        }
        if(!stage.equals("combat_finish"))save(player,session);
        player.sendMessage(Text.literal(new GsonBuilder().create().toJson(session.results)),false);return 1;
    }
    private static void sourceDamage(Entity actor,LivingEntity target,double amount){SVFrameSkript.ENGINE.invokeFunction("svamk_deal_damage",List.of(FabricPlatform.ref(actor),FabricPlatform.ref(target),amount));}
    private static ServerPlayerEntity peer(ServerPlayerEntity player){return player.getServer().getPlayerManager().getPlayerList().stream().filter(p->p!=player&&p.isAlive()&&p.getWorld()==player.getWorld()&&p.squaredDistanceTo(player)<100).findFirst().orElseThrow(()->new IllegalStateException("A second real Minecraft client must be within ten blocks"));}
    private static CowEntity cow(Session session){return session.spawned.stream().filter(e->e instanceof CowEntity).map(e->(CowEntity)e).findFirst().orElseThrow();}
    private static void ready(ServerPlayerEntity player){player.timeUntilRegen=0;player.hurtTime=0;player.setHealth(player.getMaxHealth());}
    private static Object get(String property,ServerPlayerEntity player){return SVFrameSkript.ENGINE.variables().get("svamk."+property+"::"+player.getUuid(),Map.of());}
    private static void set(String property,ServerPlayerEntity player,Object value){SVFrameSkript.ENGINE.variables().set("svamk."+property+"::"+player.getUuid(),value,Map.of());}
    private static double number(Object value){return value instanceof Number n?n.doubleValue():0;}
    private static void clear(ServerPlayerEntity player){for(String key:SVFrameSkript.ENGINE.variables().snapshot().keySet())if(key.startsWith("svamk.")&&key.contains(player.getUuid().toString()))SVFrameSkript.ENGINE.variables().set(key,null,Map.of());}
    private static void restore(ServerPlayerEntity player,Session session){
        session.spawned.forEach(Entity::discard);var variables=SVFrameSkript.ENGINE.variables();
        for(String key:variables.snapshot().keySet())if(key.startsWith("svamk.")&&session.actors.stream().anyMatch(id->key.contains(id.toString())))variables.set(key,null,Map.of());
        session.variables.forEach((key,value)->{if(key.startsWith("svamk.")&&session.actors.stream().anyMatch(id->key.contains(id.toString())))variables.set(key,value,Map.of());});
        var data=SVFrameMMO.playerData().get(player);data.prepareReload();session.profile.apply(data);data.reloadDefinitions();player.setHealth(Math.min(session.health,player.getMaxHealth()));SVFrameMMO.playerData().savePlayer(data);
    }
    private static void save(ServerPlayerEntity player,Session session)throws java.io.IOException {Path root=player.getServer().getRunDirectory().resolve("qa/skript");Files.createDirectories(root);Files.writeString(root.resolve("amk-combat-runtime.json"),new GsonBuilder().setPrettyPrinting().create().toJson(session.results));}
}
