package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import com.google.gson.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import vn.svframe.fantasyhub.protocol.*;
import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.stat.provider.EntityStatProvider;
import vn.svframe.svframelib.damage.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;

/** AI combat actor consuming the same catalog phases and cues as player class skills. */
public final class HeroNpcCombat {
 private static final Map<UUID,Actor> ACTORS=new HashMap<>();
 private static long ticks,castSequence;private static boolean installed;
 private record Pending(long at,Runnable run){}
 private static final class Actor {
  final HeroNpcEntity entity;Vec3d home;double arenaRadius=40;final List<HeroCatalog.Ability> defaultRotation;List<HeroCatalog.Ability> rotation;double cooldownMultiplier=1,damageMultiplier=1;final List<Pending> pending=new ArrayList<>();
  long ready,warningUntil,attackUntil,axeUntil;int next,windupTicks=40,recoveryTicks=100,warningColor=0xff7043;Vec3d warningPoint;double warningRadius;boolean warningBeam;String skill="";
  Actor(HeroNpcEntity e){entity=e;home=e.getPos();rotation=NpcSkillPolicy.resolve(HeroRuntime.catalog(),e.hero(),List.of());defaultRotation=rotation;ready=ticks+40;}
 }
 private HeroNpcCombat(){}
 public static void install(){if(installed)return;installed=true;ServerTickEvents.END_SERVER_TICK.register(server->{ticks++;for(var a:List.copyOf(ACTORS.values()))tick(a);});ServerLifecycleEvents.SERVER_STOPPING.register(server->{for(var a:ACTORS.values())a.entity.discard();ACTORS.clear();});}
 public static net.minecraft.entity.mob.MobEntity spawn(ServerWorld world,Vec3d at,String classId,String displayName,double maxHealth){
  install();classId=classId.toUpperCase(Locale.ROOT);HeroRuntime.catalog().hero(classId);
  if(!Double.isFinite(maxHealth)||maxHealth<1||maxHealth>1_000_000)throw new IllegalArgumentException("Boss health outside bounds");
  var e=Protocol.heroNpcType.create(world);if(e==null)throw new IllegalStateException("Cannot create class actor");
  e.hero(classId);e.setCustomName(Text.literal(displayName));e.setCustomNameVisible(true);e.refreshPositionAndAngles(at.x,at.y,at.z,0,0);
  e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);e.setHealth((float)maxHealth);e.addCommandTag("svframe_worldboss");
  for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
   if(slot==EquipmentSlot.HEAD&&Set.of("THOR","SORCERER_SUPREME").contains(classId))continue;
   String part=switch(slot){case HEAD->"helmet";case CHEST->"chestplate";case LEGS->"leggings";default->"boots";};
   e.equipStack(slot,vn.svframe.svframeitems.SVFrameItems.generator().generate("hero_"+classId.toLowerCase(Locale.ROOT)+"_"+part,1));
  }
  if(classId.equals("THOR"))e.equipStack(EquipmentSlot.MAINHAND,new net.minecraft.item.ItemStack(Protocol.stormbreaker));
  for(var slot:EquipmentSlot.values())e.setEquipmentDropChance(slot,0);
  if(!world.spawnEntity(e))throw new IllegalStateException("Cannot spawn class boss");ACTORS.put(e.getUuid(),new Actor(e));return e;
 }
 public static void setArena(net.minecraft.entity.mob.MobEntity entity,Vec3d center,double radius){var a=ACTORS.get(entity.getUuid());if(a==null||!Double.isFinite(radius)||radius<8||radius>128)throw new IllegalArgumentException("Invalid actor arena");a.home=center;a.arenaRadius=radius;}
 public static void configureTiming(net.minecraft.entity.mob.MobEntity entity,int windupTicks,int recoveryTicks,int rgb){
  var a=ACTORS.get(entity.getUuid());if(a==null||windupTicks<20||windupTicks>600||recoveryTicks<40||recoveryTicks>72000)throw new IllegalArgumentException("Invalid boss timing");a.windupTicks=windupTicks;a.recoveryTicks=recoveryTicks;a.warningColor=rgb&0xffffff;
 }
 public static Map<String,Object> status(net.minecraft.entity.mob.MobEntity entity){
  var a=ACTORS.get(entity.getUuid());if(a==null)return Map.of();
  String stage=ticks<a.warningUntil?"WINDUP":ticks<a.attackUntil?"ATTACK":ticks<a.ready?"RECOVERY":"READY";long end=stage.equals("WINDUP")?a.warningUntil:stage.equals("ATTACK")?a.attackUntil:a.ready;
  return Map.of("stage",stage,"remainingTicks",Math.max(0,end-ticks),"skill",a.skill,"point",a.warningPoint==null?List.of():List.of(a.warningPoint.x,a.warningPoint.y,a.warningPoint.z),"radius",a.warningRadius);
 }
 public static void validate(String classId,List<String> ids){NpcSkillPolicy.resolve(HeroRuntime.catalog(),classId,ids);}
 public static void configure(net.minecraft.entity.mob.MobEntity entity,List<String> ids,double cooldownMultiplier,double damageMultiplier){
  var actor=ACTORS.get(entity.getUuid());if(actor==null)throw new IllegalArgumentException("Unknown class boss actor");validate(actor.entity.hero(),ids);
  if(!Double.isFinite(cooldownMultiplier)||!Double.isFinite(damageMultiplier)||cooldownMultiplier<.1||cooldownMultiplier>10||damageMultiplier<0||damageMultiplier>20)throw new IllegalArgumentException("Invalid boss cooldown/damage multipliers");
  var list=NpcSkillPolicy.resolve(HeroRuntime.catalog(),actor.entity.hero(),ids);if(!list.equals(actor.rotation)){actor.rotation=list;actor.next=0;}
  actor.cooldownMultiplier=cooldownMultiplier;actor.damageMultiplier=damageMultiplier;
 }
 private static boolean legal(HeroNpcEntity actor,ServerPlayerEntity target){return (ACTORS.get(actor.getUuid())==null||target.getPos().squaredDistanceTo(ACTORS.get(actor.getUuid()).home)<=Math.pow(ACTORS.get(actor.getUuid()).arenaRadius,2))&&!target.getCommandTags().contains("svframe_cosmic_peace")&&target.isAlive()&&!target.isCreative()&&!target.isSpectator()&&target.getWorld()==actor.getWorld()&&!actor.isTeammate(target)&&RpgEntityAdapters.allows(actor,target,RpgEntityAdapters.Effect.DAMAGE);}
 private static void tick(Actor a){
  var e=a.entity;if(e.getCommandTags().contains("svframe_cosmic_frozen")){a.ready++;if(a.warningUntil>0)a.warningUntil++;if(a.attackUntil>0)a.attackUntil++;if(a.axeUntil>0)a.axeUntil++;for(int i=0;i<a.pending.size();i++){var p=a.pending.get(i);a.pending.set(i,new Pending(p.at+1,p.run));}return;}if(e.isRemoved()||!e.isAlive()){ACTORS.remove(e.getUuid());return;}
  if(e.getPos().squaredDistanceTo(a.home)>a.arenaRadius*a.arenaRadius){e.getNavigation().startMovingTo(a.home.x,a.home.y,a.home.z,1.3);a.pending.clear();a.warningPoint=null;a.warningUntil=a.attackUntil=0;return;}
  if(a.warningPoint!=null&&ticks<a.warningUntil&&ticks%4==0)warning(a);
  for(var pending:List.copyOf(a.pending))if(pending.at<=ticks){a.pending.remove(pending);pending.run.run();}
  var world=(ServerWorld)e.getWorld();var target=world.getPlayers(p->legal(e,p)&&p.squaredDistanceTo(e)<40*40).stream().min(Comparator.comparingDouble(e::squaredDistanceTo)).orElse(null);
  if(ticks%10==0)state(e);
  if(target==null){e.getNavigation().stop();return;}e.setTarget(target);e.getLookControl().lookAt(target,40,40);
  e.setYaw((float)(Math.toDegrees(Math.atan2(target.getZ()-e.getZ(),target.getX()-e.getX()))-90));e.setBodyYaw(e.getYaw());
  if(ticks<a.warningUntil)e.getNavigation().stop();else if(e.squaredDistanceTo(target)>36)e.getNavigation().startMovingTo(target,1.15);else e.getNavigation().stop();
  if(ticks<a.ready||a.rotation.isEmpty())return;
  var ability=a.rotation.get(a.next%a.rotation.size());double range=Math.clamp(ability.range(),2,40);
  if(e.squaredDistanceTo(target)>range*range){e.getNavigation().startMovingTo(target,1.15);return;}
  a.next++;a.ready=ticks+NpcSkillPolicy.cycleTicks(ability,a.windupTicks,a.recoveryTicks,a.cooldownMultiplier);a.attackUntil=a.ready-Math.max(20,(long)(a.recoveryTicks*a.cooldownMultiplier));cast(a,target,ability);
 }
 private static void cast(Actor actor,ServerPlayerEntity target,HeroCatalog.Ability ability){
  var e=actor.entity;var goal=target.getPos();long id=++castSequence;
  actor.skill=ability.id();actor.warningPoint=goal;actor.warningUntil=ticks+actor.windupTicks;actor.warningBeam=ability.key().equals("STORMBREAKER_THROW")||ability.radius()<.1&&!Set.of("CAPE_COMBO","AIR_COMBO").contains(ability.key());actor.warningRadius=Math.max(actor.warningBeam?1.1:2.5,Math.min(12,ability.radius()));if(ability.key().equals("STORMBREAKER_THROW")){var start=e.getEyePos();var end=start.add(goal.add(0,1.4,0).subtract(start).normalize().multiply(20));var hit=e.getWorld().raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,e));actor.warningPoint=(hit.getType()==HitResult.Type.MISS?end:hit.getPos()).add(0,-1.4,0);}warning(actor);
  ((ServerWorld)e.getWorld()).playSound(null,e.getBlockPos(),SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),SoundCategory.HOSTILE,.8f,.7f);
  actor.pending.add(new Pending(actor.warningUntil,()->{actor.warningPoint=null;if(e.isAlive()&&!e.isRemoved())attack(actor,target,ability,id,goal);}));
 }
 private static void warning(Actor actor){
  var p=actor.warningPoint;if(p==null)return;int c=actor.warningColor;var particle=new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f),1.3f);var world=(ServerWorld)actor.entity.getWorld();
  for(int i=0;i<56;i++){double a=i*Math.PI/28;world.spawnParticles(particle,p.x+Math.cos(a)*actor.warningRadius,p.y+.12,p.z+Math.sin(a)*actor.warningRadius,1,0,0,0,0);}
  if(actor.warningBeam){var from=actor.entity.getPos();var d=p.subtract(from);var side=new Vec3d(-d.z,0,d.x).normalize().multiply(actor.warningRadius);int steps=Math.min(80,Math.max(2,(int)(d.length()*2)));for(int i=0;i<=steps;i++){var at=from.lerp(p,i/(double)steps);for(int sign:new int[]{-1,1})world.spawnParticles(particle,at.x+side.x*sign,at.y+.12,at.z+side.z*sign,1,0,0,0,0);}}
 }
 private static void attack(Actor actor,ServerPlayerEntity target,HeroCatalog.Ability ability,long id,Vec3d goal){
  var e=actor.entity;var from=e.getPos();
  double duration=Math.max(.8,ability.phases().stream().mapToDouble(p->Math.max(0,p.start())+(p.hits()-1)*p.interval()).max().orElse(.8));
  cue(e,ability,"CAST",from,goal,Math.min(6,duration+.7),id);
  ((ServerWorld)e.getWorld()).playSound(null,e.getBlockPos(),sound(e.hero()),SoundCategory.HOSTILE,1.3f,.85f);
  // A visible wind-up gives players time to leave the hit area before the first damage phase.
  int windup=0;String key=ability.key();
  if(key.equals("SLING_RING")||key.equals("THUNDEROUS_SLAM")||key.equals("THRUSTER_DASH")||key.equals("WEB_BLITZ")||key.equals("INFINITE_MASS_PUNCH"))actor.pending.add(new Pending(ticks+windup,()->{
   if(!legal(e,target))return;var position=goal.add(target.getRotationVec(1).multiply(-1.5,0,-1.5));
   if(e.getWorld().isSpaceEmpty(e,e.getBoundingBox().offset(position.subtract(e.getPos()))))e.refreshPositionAfterTeleport(position);
  }));
  if(key.equals("GOD_OF_THUNDER")){e.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,200,1));e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,200,0));}
  if(key.equals("WEB_SHIELD")||key.equals("TAO_MANDALAS"))e.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION,120,2));
  if(key.equals("SUPER_SPEED"))e.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,160,2));
  if(key.equals("SLOW_MOTION")||key.equals("SPEED_SIPHON"))for(var player:((ServerWorld)e.getWorld()).getPlayers(p->legal(e,p)&&e.squaredDistanceTo(p)<100))player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,80,1));
  if(key.equals("ASTRAL_PROJECTION")){e.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY,80));e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,80,2));}
  if(key.equals("SMOKE_TAKEDOWN"))e.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY,80));
  if(key.equals("MOLECULAR_PHASING"))e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE,60,1));
  if(key.equals("BATCLAW"))actor.pending.add(new Pending(ticks+windup,()->{
   if(legal(e,target)&&RpgEntityAdapters.allows(e,target,RpgEntityAdapters.Effect.MOVEMENT)){cue(e,ability,"TETHER",e.getEyePos(),target.getEyePos(),.8,id);target.addVelocity(e.getPos().subtract(target.getPos()).normalize().multiply(.7));target.velocityModified=true;target.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(target));}
  }));
  if(key.equals("STORMBREAKER_THROW")){npcAxe(actor,goal,ability,id,windup);return;}
  for(var phase:ability.phases())for(int i=0;i<phase.hits();i++){
   long at=ticks+windup+(long)((Math.max(0,phase.start())+i*phase.interval())*20);
   actor.pending.add(new Pending(at,()->{
    if(!e.isAlive())return;
    var endpoint=goal;
    boolean beam=ability.radius()<.1&&!Set.of("CAPE_COMBO","AIR_COMBO").contains(key);
    if(key.equals("MJOLNIR_THROW"))cue(e,ability,"WEAPON_THROW",e.getEyePos(),endpoint.add(0,1,0),.5,id);
    else if(beam)cue(e,ability,"BEAM",e.getEyePos(),endpoint.add(0,1,0),.35,id);
    cue(e,ability,"IMPACT",endpoint,endpoint,.8,id);
    double radius=Math.max(beam?1.1:2.5,Math.min(12,ability.radius()));
    var box=beam?new Box(e.getEyePos(),endpoint.add(0,1,0)).expand(radius):Box.of(endpoint,radius*2,radius*2,radius*2);
    for(var victim:((ServerWorld)e.getWorld()).getPlayers(p->legal(e,p)&&box.intersects(p.getBoundingBox()))){
     if(!beam&&victim.squaredDistanceTo(endpoint)>radius*radius)continue;
     if(beam&&!segment(e.getEyePos(),endpoint.add(0,1,0),victim,radius))continue;
     if(e.getWorld().raycast(new RaycastContext(e.getEyePos(),victim.getEyePos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,e)).getType()!=HitResult.Type.MISS)continue;
     damage(e,victim,phase,actor.damageMultiplier);
     if(Set.of("COCOON_SLAM","WORLD_WEB_TRAP","MIRROR_DIMENSION","WIND_FUNNEL","CAPE_COMBO").contains(key)&&RpgEntityAdapters.allows(e,victim,RpgEntityAdapters.Effect.STATUS))victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,35,1));
    }
   }));
  }
 }
 private static void npcAxe(Actor actor,Vec3d goal,HeroCatalog.Ability ability,long id,int windup){
  var e=actor.entity;Vec3d start=e.getEyePos(),end=start.add(goal.add(0,1.4,0).subtract(start).normalize().multiply(20));
  var world=(ServerWorld)e.getWorld();var clipped=world.raycast(new RaycastContext(start,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,e));if(clipped.getType()!=HitResult.Type.MISS)end=clipped.getPos();
  actor.axeUntil=ticks+windup+28;state(e);actor.pending.add(new Pending(actor.axeUntil,()->{state(e);cue(e,ability,"AXE_END",e.getEyePos(),e.getEyePos(),0,id);}));
  final Vec3d endpoint=end;var hits=new HashSet<UUID>();var returns=new HashSet<UUID>();
  for(int step=0;step<28;step++){final int index=step;actor.pending.add(new Pending(ticks+windup+step,()->{
   boolean recall=index>=14;double previous=(index%14)/14d,next=(index%14+1)/14d;
   Vec3d a=recall?endpoint.lerp(e.getEyePos(),previous):start.lerp(endpoint,previous),b=recall?endpoint.lerp(e.getEyePos(),next):start.lerp(endpoint,next);
   cue(e,ability,"AXE",a,b,.12,id);cue(e,ability,"TRAIL",a,b,.25,id);
   for(var victim:world.getPlayers(p->legal(e,p)&&segment(a,b,p,.9))){if((recall?returns:hits).contains(victim.getUuid()))continue;
    if(world.raycast(new RaycastContext(a.lerp(victim.getEyePos(),.001),victim.getEyePos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,e)).getType()!=HitResult.Type.MISS)continue;
    (recall?returns:hits).add(victim.getUuid());damage(e,victim,ability.phases().get(recall?1:0),actor.damageMultiplier);
   }
  }));}
 }
 private static boolean segment(Vec3d from,Vec3d to,LivingEntity target,double radius){var box=target.getBoundingBox().expand(radius);return box.contains(from)||box.raycast(from,to).isPresent();}
 private static void damage(HeroNpcEntity e,LivingEntity victim,HeroCatalog.Phase phase,double multiplier){
  double amount=phase.kind().equals("MAX_HEALTH")?victim.getMaxHealth()*phase.scale():e.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE)*Math.min(4,phase.scale());
  var metadata=new DamageMetadata(amount*multiplier,List.of(DamageType.SKILL,phase.kind().contains("MAGIC")?DamageType.MAGIC:DamageType.PHYSICAL));
  SVFrameLib.inst().getDamage().registerAttack(new AttackMetadata(metadata,victim,new EntityStatProvider(e)),false,true,e.getDamageSources().mobAttack(e));
 }
 private static SoundEvent sound(String id){return switch(id){case "THOR"->SoundEvents.ITEM_TRIDENT_THUNDER.value();case "SORCERER_SUPREME"->SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE;case "IRON_MAN"->SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH;case "BATMAN"->SoundEvents.ENTITY_BAT_TAKEOFF;default->SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP;};}
 private static JsonArray vector(Vec3d p){var a=new JsonArray();a.add(p.x);a.add(p.y);a.add(p.z);return a;}
 private static void cue(HeroNpcEntity e,HeroCatalog.Ability ability,String phase,Vec3d from,Vec3d to,double duration,long cast){var d=base(e);d.addProperty("kind","cue");d.addProperty("ability",ability.key());d.addProperty("phase",phase);d.addProperty("duration",duration);d.addProperty("radius",ability.radius());d.addProperty("cast",cast);d.add("from",vector(from));d.add("to",vector(to));send(e,d);}
 private static void state(HeroNpcEntity e){var d=base(e);d.addProperty("kind","state");var actor=ACTORS.get(e.getUuid());d.addProperty("flags",actor!=null&&ticks<actor.axeUntil?1<<12:0);d.addProperty("stacks",0);d.addProperty("speed",0);d.addProperty("heat",0);send(e,d);}
 private static JsonObject base(HeroNpcEntity e){var d=new JsonObject();d.addProperty("entity",e.getId());d.addProperty("hero",e.hero());return d;}
 private static void send(HeroNpcEntity e,JsonObject d){for(var p:PlayerLookup.tracking(e))if(ServerPlayNetworking.canSend(p,Protocol.Hero.ID))ServerPlayNetworking.send(p,new Protocol.Hero(d.toString()));}
}
