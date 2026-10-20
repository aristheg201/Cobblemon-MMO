package vn.svframe.fantasyhub.core.hero;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.modifier.StatModifier;
import vn.svframe.svframelib.skill.*;
import vn.svframe.svframelib.skill.handler.SkillHandler;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.util.configobject.MapConfigObject;
import vn.svframe.svframemmo.SVFrameMMO;

/** Class session/action coordinator. MMO remains the owner of resources, skill costs and saves. */
public final class HeroRuntime {
  private static HeroCatalog catalog;
  private static final Map<UUID,Session> SESSIONS=new HashMap<>();
  static final HeroTimeline TIMELINE=new HeroTimeline();
  static MinecraftServer server;
  static long tick,sequence;
  private static boolean installed;
  public static HeroCatalog catalog(){return catalog;}

  static final class Session {
    final ServerPlayerEntity player;
    final ServerWorld world;
    final HeroCatalog.Hero hero;
    final HeroPowerState power=new HeroPowerState();
    final Map<String,StatModifier> stats=new HashMap<>();
    vn.svframe.svframelib.player.skillmod.SkillModifier skillCooldown;
    String cooldownSignature="";
    final Map<String,Double> appliedStats=new HashMap<>();
    final Map<String,Cast> recasts=new HashMap<>();
    final Map<Long,Cast> casts=new HashMap<>();
    final List<Runnable> cleanup=new ArrayList<>();
    final boolean priorFlight,priorFlying,priorNoClip;
    final float priorFlySpeed;
    Vec3d lastPosition,lastSafe,returnAnchor,channelAnchor;
    long lastInput,lastJump=-100,lastHit=-100,emergencyReady,movementTicks;
    int inputFlags,heat,speedLevel,standingTicks;
    boolean jumping,emergencyPending;
    Session(ServerPlayerEntity p,HeroCatalog.Hero hero) {
      player=p;world=p.getServerWorld();this.hero=hero;
      priorFlight=p.getAbilities().allowFlying;priorFlying=p.getAbilities().flying;
      priorFlySpeed=p.getAbilities().getFlySpeed();priorNoClip=p.noClip;
      lastPosition=lastSafe=p.getPos();
    }
    double passive(String key){return hero.passive(key);}
    boolean active(String key){return power.active(key,tick);}
    void flag(String key,double seconds){power.flag(key,(int)Math.round(seconds*20),tick);}
  }
  static final class Cast {
    final Session owner;
    final HeroCatalog.Ability ability;
    final long id=++sequence,created=tick,expires;
    final int generation;
    final Vec3d origin;
    final HeroCastBudget budget;
    final double attack;
    final boolean empowered;
    final Map<String,Set<UUID>> hits=new HashMap<>();
    LivingEntity target;
    Vec3d point;
    boolean proc,award,burstDone,recastUsed;
    int projectileCount;
    Cast(Session s,HeroCatalog.Ability a,LivingEntity target,Vec3d point) {
      owner=s;ability=a;this.target=target;this.point=point;origin=s.player.getPos();generation=s.power.generation();
      attack=Math.max(1,Math.max(s.player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE),MMOPlayerData.setup(s.player).getStatMap().getStat("ATTACK_DAMAGE")));
      empowered=s.hero.id().equals("SORCERER_SUPREME")&&s.power.stacks("mystic",tick)>=s.passive("mystic_max");
      double last=a.phases().stream().mapToDouble(p->Math.max(0,p.start())+Math.max(0,p.hits()-1)*p.interval()).max().orElse(0);
      expires=tick+(long)((Math.max(a.duration(),Math.max(a.recast(),last))+6)*20);
      budget=new HeroCastBudget(catalog.setting(a.ultimate()?"ultimate_cast_hp_cap":"regular_cast_hp_cap"));
    }
    boolean valid(){return HeroRuntime.valid(owner)&&generation==owner.power.generation()&&tick<=expires;}
    boolean once(String group,UUID target){return hits.computeIfAbsent(group,k->new HashSet<>()).add(target);}
    void later(double seconds,Runnable action){TIMELINE.schedule(owner.player.getUuid(),tick+Math.max(1,(int)Math.round(seconds*20)),this::valid,action);}
    int cap(){return (int)catalog.setting(ability.ultimate()?"ultimate_max_targets":"max_targets");}
    double total(){return ability.totalCoefficient()+(empowered?owner.passive("mystic_burst"):0);}
  }
  record Prepared(boolean isSuccessful,Session session,HeroCatalog.Ability ability,LivingEntity target,Vec3d point,Cast recast) implements SkillResult {}

  public static void install() {
    if(installed)return;installed=true;reload();
    NativeSkillProviders.register("fantasyhub_heroes",new NativeSkillProviders.Provider() {
      public boolean available(String id){return catalog.abilities().containsKey(id.toUpperCase(Locale.ROOT))&&HeroSkillRouter.abilities().contains(id.toUpperCase(Locale.ROOT));}
      public void reloadDefinitions(){HeroRuntime.reload();}
      public void materialize(vn.svframe.svframelib.manager.SkillManager manager){materializeHandlers();}
      public boolean cast(String id,UUID caster,UUID target,Map<String,?> parameters) {
        var player=server==null?null:server.getPlayerManager().getPlayer(caster);if(player==null||!available(id))return false;
        var result=prepare(player,catalog.ability(id.toUpperCase(Locale.ROOT)));if(!result.isSuccessful)return false;
        // Trusted provider callbacks are already paid by their outer native/script skill owner.
        execute(result);return true;
      }
    });
    materializeHandlers();
    SkillCastPolicies.register("fantasyhub:heroes",(uuid,id)->{
      var p=server==null?null:server.getPlayerManager().getPlayer(uuid);
      if(p!=null&&HeroControl.silenced(p))return false;
      var s=SESSIONS.get(uuid);return s==null||!s.active("busy");
    },(uuid,id)->{var s=SESSIONS.get(uuid);return s==null?1:catalog.setting("cooldown_reduction_cap");});
    SVFrameMMO.skillRuntime().registerRecastWindow("fantasyhub:heroes",(data,skill)->{
      var s=SESSIONS.get(data.getUniqueId());String id=skill.getSkill().getId();
      return s!=null&&valid(s)&&(s.power.canRecast(id,tick)||id.equals("FLASH_SUPER_SPEED")&&s.active("super_speed"));
    });
    HeroCombat.install();HeroPassives.install();HeroQa.install();
    vn.svframe.svframelib.entity.RpgEntityAdapters.register("fantasyhub:heroes",new vn.svframe.svframelib.entity.RpgEntityAdapters.Adapter(){
      public boolean supports(LivingEntity target){return false;}
      public boolean allows(Entity actor,LivingEntity target,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect effect){return true;}
      public boolean allowsFrom(Entity actor,LivingEntity target,vn.svframe.svframelib.entity.RpgEntityAdapters.Effect effect){return effect!=vn.svframe.svframelib.entity.RpgEntityAdapters.Effect.MOVEMENT||!controlImmune(target)&&(actor!=target||!HeroControl.mobilityLocked(target));}
    });
    ServerLifecycleEvents.SERVER_STARTING.register(s->{server=s;tick=0;reload();});
    ServerTickEvents.END_SERVER_TICK.register(s->{tick++;HeroCombat.tick();HeroControl.tick(tick);HeroBarriers.tick(tick);TIMELINE.tick(tick);
      for(var p:s.getPlayerManager().getPlayerList()) {
        var session=session(p);if(session==null)continue;
        HeroPassives.tick(session);session.casts.values().removeIf(c->!c.valid());
        session.recasts.entrySet().removeIf(e->!session.power.canRecast(e.getKey(),tick));
        HeroPresentation.publish(session);
      }
      for(var owner:List.copyOf(SESSIONS.values()))if(!valid(owner))close(owner);
    });
    ServerPlayConnectionEvents.DISCONNECT.register((handler,s)->{var owner=SESSIONS.get(handler.player.getUuid());if(owner!=null)close(owner);});
    ServerLifecycleEvents.SERVER_STOPPING.register(s->{for(var owner:List.copyOf(SESSIONS.values()))close(owner);TIMELINE.clear();HeroControl.clear();HeroBarriers.clear();server=null;});
  }
  static Session session(ServerPlayerEntity p) {
    if(catalog==null||p.isRemoved()||!p.isAlive()||p.isSpectator())return null;
    String id=SVFrameMMO.playerData().get(p).getClassId().toUpperCase(Locale.ROOT);
    var current=SESSIONS.get(p.getUuid());
    if(current!=null&&(current.player!=p||!current.hero.id().equals(id)||current.world!=p.getServerWorld())){close(current);current=null;}
    if(!catalog.heroes().containsKey(id))return null;
    if(current==null){current=new Session(p,catalog.hero(id));SESSIONS.put(p.getUuid(),current);}
    return current;
  }
  static boolean valid(Session s){return s.player.isAlive()&&!s.player.isRemoved()&&!s.player.isSpectator()&&s.player.networkHandler!=null&&s.world==s.player.getWorld()&&SESSIONS.get(s.player.getUuid())==s&&SVFrameMMO.playerData().get(s.player).getClassId().equalsIgnoreCase(s.hero.id());}
  static void resetForQa(ServerPlayerEntity player){var s=SESSIONS.get(player.getUuid());if(s!=null)close(s);}
  private static void close(Session s) {
    HeroPassives.restoreMovement(s);if(s.skillCooldown!=null)s.skillCooldown.unregister(MMOPlayerData.setup(s.player));s.power.clear();TIMELINE.cancel(s.player.getUuid());
    for(var modifier:s.stats.values())modifier.unregister(MMOPlayerData.setup(s.player));s.stats.clear();s.appliedStats.clear();
    for(var action:s.cleanup)action.run();s.cleanup.clear();s.casts.clear();s.recasts.clear();
    HeroControl.release(s.player.getUuid());HeroBarriers.release(s.player.getUuid());SESSIONS.remove(s.player.getUuid(),s);
    HeroPresentation.remove(s);
  }
  private static void reload() {
    try {
      HeroBootstrap.install();
      var next=HeroCatalog.parse(JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("FantasyHub/heroes.json"))).getAsJsonObject());
      for(var s:List.copyOf(SESSIONS.values()))close(s);catalog=next;HeroControl.configure(next);
      if(installed&&SVFrameLib.inst()!=null)materializeHandlers();
    } catch(IOException failure){throw new IllegalStateException("Cannot load superhero definitions",failure);}
  }
  private static void materializeHandlers(){for(var a:catalog.abilities().values())SVFrameLib.inst().getSkills().replaceSkillHandler(new Handler(a));}
  private static Prepared prepare(ServerPlayerEntity p,HeroCatalog.Ability ability) {
    var s=session(p);boolean ok=s!=null&&valid(s)&&!s.active("busy")&&ability.id().startsWith(s.hero.id()+"_")&&!HeroControl.silenced(p);
    var existing=s==null?null:s.recasts.get(ability.id());boolean recast=existing!=null&&s.power.canRecast(ability.id(),tick);
    LivingEntity target=recast?existing.target:HeroWorld.aim(p,ability.range());
    Vec3d point=recast?existing.point:HeroWorld.point(p,ability.range());
    if(ok&&HeroSkillRouter.requiresTarget(ability.id())&&(target==null||!HeroWorld.legal(p,target)))ok=false;
    if(ok&&ability.key().equals("SLING_RING")){point=HeroWorld.landing(p,point);ok=point!=null;}
    if(ok&&ability.key().equals("WEB_BLITZ"))ok=!HeroWorld.near(p,p.getPos(),ability.range(),3).isEmpty();
    if(ok&&HeroControl.mobilityLocked(p)&&HeroSkillRouter.mobility(ability.id()))ok=false;
    return new Prepared(ok,s,ability,target,point,existing);
  }
  private static void execute(Prepared prepared) {
    var s=prepared.session;var a=prepared.ability;Cast cast;
    if(prepared.recast!=null&&s.power.recast(a.id(),tick)){cast=prepared.recast;cast.recastUsed=true;}
    else {cast=new Cast(s,a,prepared.target,prepared.point);s.casts.put(cast.id,cast);
      if(a.recast()>0){s.power.openRecast(a.id(),(int)(a.recast()*20),tick);s.recasts.put(a.id(),cast);}
      HeroPassives.castStarted(cast);
    }
    HeroPresentation.cue(cast,"CAST",s.player.getPos(),cast.point,Math.min(6,Math.max(.5,Math.max(a.duration(),a.phases().stream().mapToDouble(ph->Math.max(0,ph.start())+(ph.hits()-1)*ph.interval()).max().orElse(a.castTime())))));
    HeroSkillRouter.execute(cast);
  }
  public static void input(ServerPlayerEntity p,int flags) {
    if(flags<0||flags>31)return;var s=session(p);if(s==null||!valid(s)||tick-s.lastInput<1)return;
    s.inputFlags=flags;s.lastInput=tick;HeroPassives.input(s,flags);
  }
  public static boolean controlImmune(LivingEntity e){var s=SESSIONS.get(e.getUuid());return s!=null&&valid(s)&&(s.active("control_immune")||s.active("astral"));}
  public static boolean statusImmune(LivingEntity e,RegistryEntry<StatusEffect> effect) {
    var s=SESSIONS.get(e.getUuid());if(s==null||!valid(s))return false;
    return controlImmune(e)&&effect.equals(StatusEffects.SLOWNESS)||s.hero.id().equals("FLASH")&&(effect.equals(StatusEffects.POISON)||effect.equals(StatusEffects.WITHER)||effect.equals(StatusEffects.NAUSEA));
  }
  public static Map<String,Object> inspect(ServerPlayerEntity p) {
    var s=session(p);if(s==null)return Map.of();
    var info=new LinkedHashMap<String,Object>();
    info.put("class",s.hero.id());info.put("resource",s.hero.resourceName());info.put("shield",HeroBarriers.amount(p.getUuid()));info.put("speedLevel",s.speedLevel);info.put("heat",s.heat);
    for(String key:List.of("super_speed","flight","phase","astral","overdrive","awakened"))info.put(key,s.active(key));
    // Keep the existing inspector keys while exposing native passive observations.
    info.put("superSpeed",s.active("super_speed"));
    for(String key:List.of("instinct","mystic","static","combo"))info.put(key,s.power.stacks(key,tick));
    info.put("pendingActions",TIMELINE.pending());return info;
  }
  private static final class Handler extends SkillHandler<Prepared> implements NativeSkillProviders.BackedHandler {
    final HeroCatalog.Ability ability;
    Handler(HeroCatalog.Ability a){super(new MapConfigObject(a.id(),Map.of("name",a.name(),"trigger","CAST","parameters",Map.of("cooldown",a.cooldown(),"mana",catalog.hero(a.id().substring(0,a.id().length()-a.key().length()-1)).resource().equals("MANA")?a.cost():0,"stamina",catalog.hero(a.id().substring(0,a.id().length()-a.key().length()-1)).resource().equals("STAMINA")?a.cost():0),"icon",Map.of("material","minecraft:nether_star"),"lore",List.of(a.nameVi(),"{cooldown}s • {mana} mana • {stamina} stamina"))));ability=a;}
    public Prepared getResult(SkillMetadata meta){return prepare(meta.getCaster().getPlayer(),ability);}
    public void whenCast(Prepared result,SkillMetadata meta){if(result.isSuccessful)execute(result);}
    public String providerNamespace(){return "fantasyhub_heroes";}
    public String sourceSkill(){return ability.id();}
  }
}
