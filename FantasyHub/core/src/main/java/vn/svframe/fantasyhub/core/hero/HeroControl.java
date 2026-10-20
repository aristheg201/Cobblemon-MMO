package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.entity.RpgEntityAdapters;

/** Owned temporary status leases; no persistent NoAI flags or permanent attribute edits. */
public final class HeroControl {
  enum Kind { ROOT,STUN,FREEZE,AIR_LOCK,SILENCE,MOBILITY_LOCK }
  private record Pin(UUID owner,LivingEntity target,ServerWorld world,Kind kind,Vec3d at,long until) {}
  private record Soft(UUID owner,LivingEntity target,ServerWorld world,net.minecraft.registry.entry.RegistryEntry<EntityAttribute> attribute,double fraction,long until) {}
  private static final Map<String,Pin> PINS=new HashMap<>();
  private static final Map<String,Soft> SOFT=new HashMap<>();
  private static HeroControlDiminishingReturns dr=new HeroControlDiminishingReturns();
  private static long tick;
  private HeroControl() {}
  static void configure(HeroCatalog catalog){dr=new HeroControlDiminishingReturns((int)catalog.setting("pvp_control_cap_ticks"),(int)catalog.setting("control_window_ticks"));}
  static int apply(HeroRuntime.Session s,LivingEntity target,Kind kind,double seconds) {
    if(!HeroRuntime.valid(s)||!RpgEntityAdapters.allows(s.player,target,RpgEntityAdapters.Effect.STATUS)
        ||(kind!=Kind.SILENCE&&!RpgEntityAdapters.allows(s.player,target,RpgEntityAdapters.Effect.MOVEMENT))||HeroRuntime.controlImmune(target))return 0;
    int duration=Math.max(0,(int)Math.round(seconds*20));
    if(kind==Kind.SILENCE&&target instanceof ServerPlayerEntity)duration=Math.min(duration,(int)HeroRuntime.catalog().setting("pvp_silence_cap_ticks"));
    if(kind!=Kind.SILENCE&&kind!=Kind.MOBILITY_LOCK)duration=dr.duration(target.getUuid(),tick,duration,target instanceof ServerPlayerEntity);
    if(duration<=0)return 0;
    String key=s.player.getUuid()+":"+target.getUuid()+":"+kind;
    PINS.put(key,new Pin(s.player.getUuid(),target,s.world,kind,target.getPos(),tick+duration));
    return duration;
  }
  public static boolean stunned(LivingEntity e){return has(e,Kind.STUN)||has(e,Kind.FREEZE)||has(e,Kind.AIR_LOCK);}
  public static boolean rooted(LivingEntity e){return has(e,Kind.ROOT)||stunned(e);}
  public static boolean silenced(LivingEntity e){return has(e,Kind.SILENCE)||stunned(e);}
  public static boolean mobilityLocked(LivingEntity e){return rooted(e)||has(e,Kind.MOBILITY_LOCK);}
  static boolean has(LivingEntity e,Kind kind) {
    for(var p:PINS.values())if(p.target==e&&p.kind==kind&&p.until>tick&&p.world==e.getWorld())return true;
    return false;
  }
  static void slow(HeroRuntime.Session s,LivingEntity t,double fraction,double seconds) {
    soft(s,t,EntityAttributes.GENERIC_MOVEMENT_SPEED,fraction,seconds);
  }
  static void soft(HeroRuntime.Session s,LivingEntity t,net.minecraft.registry.entry.RegistryEntry<EntityAttribute> attribute,double fraction,double seconds) {
    if(!RpgEntityAdapters.allows(s.player,t,RpgEntityAdapters.Effect.MOVEMENT)||HeroRuntime.controlImmune(t))return;
    if(attribute==EntityAttributes.GENERIC_MOVEMENT_SPEED&&t instanceof ServerPlayerEntity)fraction=Math.min(fraction,HeroRuntime.catalog().setting("pvp_slow_cap"));
    String key=s.player.getUuid()+":"+t.getUuid()+":"+attribute.getIdAsString();
    SOFT.put(key,new Soft(s.player.getUuid(),t,s.world,attribute,Math.max(0,Math.min(1,fraction)),tick+(int)(seconds*20)));
    refresh(t,attribute);
  }
  private static Identifier id(net.minecraft.registry.entry.RegistryEntry<EntityAttribute> attr){return Identifier.of("fantasyhub","hero_debuff_"+attr.getIdAsString().replace(':','_').replace('.','_'));}
  private static void refresh(LivingEntity target,net.minecraft.registry.entry.RegistryEntry<EntityAttribute> attribute) {
    var instance=target.getAttributeInstance(attribute);if(instance==null)return;
    var key=id(attribute);instance.removeModifier(key);
    double max=SOFT.values().stream().filter(e->e.target==target&&e.attribute==attribute&&e.until>tick).mapToDouble(Soft::fraction).max().orElse(0);
    if(max>0)instance.addTemporaryModifier(new EntityAttributeModifier(key,-max,EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
  }
  static void tick(long now) {
    tick=now;
    PINS.values().removeIf(p->p.until<=now||!p.target.isAlive()||p.target.isRemoved()||p.target.getWorld()!=p.world);
    var expired=new ArrayList<Soft>();SOFT.values().removeIf(s->{boolean remove=s.until<=now||!s.target.isAlive()||s.target.isRemoved()||s.target.getWorld()!=s.world;if(remove)expired.add(s);return remove;});
    expired.forEach(s->refresh(s.target,s.attribute));
    var visited=new HashSet<UUID>();
    for(var pin:PINS.values().stream().sorted(Comparator.comparingInt((Pin p)->p.kind==Kind.AIR_LOCK?0:p.kind==Kind.FREEZE?1:p.kind==Kind.STUN?2:3)).toList()) {
      if(pin.kind==Kind.SILENCE||pin.kind==Kind.MOBILITY_LOCK||!visited.add(pin.target.getUuid()))continue;
      LivingEntity target=pin.target;
      boolean hard=stunned(target);Vec3d at=pin.at;
      if(!hard)at=new Vec3d(at.x,target.getY(),at.z);
      HeroWorld.velocity(target,hard?Vec3d.ZERO:new Vec3d(0,target.getVelocity().y,0));
      if(target.squaredDistanceTo(at)>.01) {
        if(target instanceof ServerPlayerEntity p){if(HeroWorld.safe(p,at,true))HeroWorld.teleport(p,at);}
        else if(pin.world.isSpaceEmpty(target,target.getBoundingBox().offset(at.subtract(target.getPos()))))target.setPosition(at);
      }
    }
    if(now%20==0)dr.expire(now);
  }
  static void cleanseSlow(LivingEntity target) {
    var removed=new ArrayList<Soft>();
    SOFT.values().removeIf(s->{if(s.target==target&&s.attribute==EntityAttributes.GENERIC_MOVEMENT_SPEED){removed.add(s);return true;}return false;});removed.forEach(s->refresh(s.target,s.attribute));
  }
  static void cleanse(LivingEntity target) {
    PINS.values().removeIf(p->p.target==target);var removed=new ArrayList<Soft>();
    SOFT.values().removeIf(s->{if(s.target==target){removed.add(s);return true;}return false;});removed.forEach(s->refresh(s.target,s.attribute));
  }
  static void release(UUID owner) {
    PINS.values().removeIf(p->p.owner.equals(owner));var removed=new ArrayList<Soft>();
    SOFT.values().removeIf(s->{if(s.owner.equals(owner)){removed.add(s);return true;}return false;});removed.forEach(s->refresh(s.target,s.attribute));
  }
  static void clear(){var copy=new ArrayList<>(SOFT.values());SOFT.clear();PINS.clear();copy.forEach(s->refresh(s.target,s.attribute));}
}
