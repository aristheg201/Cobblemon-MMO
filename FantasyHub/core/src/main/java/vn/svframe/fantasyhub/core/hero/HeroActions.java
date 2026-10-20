package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import java.util.function.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.player.resource.ResourceUpdateReason;
import vn.svframe.svframemmo.SVFrameMMO;

/** Shared bounded world actions. All delayed callbacks are session/generation validated. */
final class HeroActions {
  static void effect(LivingEntity target,RegistryEntry<StatusEffect> effect,double seconds,int level) {
    target.addStatusEffect(new StatusEffectInstance(effect,Math.max(1,(int)(seconds*20)),level,false,false,true));
  }
  static void effect(HeroRuntime.Cast c,LivingEntity target,RegistryEntry<StatusEffect> effect,double seconds,int level) {
    if(RpgEntityAdapters.allows(c.owner.player,target,RpgEntityAdapters.Effect.STATUS))effect(target,effect,seconds,level);
  }
  static void shield(HeroRuntime.Cast c,LivingEntity target,double fraction,double duration) {
    HeroBarriers.add(c.owner.player.getUuid(),target.getUuid(),c.ability.id(),target.getMaxHealth(),fraction,HeroRuntime.tick+(int)(duration*20));
  }
  static void cleanse(LivingEntity target) {
    HeroControl.cleanse(target);
    for(var effect:List.copyOf(target.getStatusEffects()))if(!effect.getEffectType().value().isBeneficial())target.removeStatusEffect(effect.getEffectType());
  }
  static void debuffs(HeroRuntime.Cast c,LivingEntity t) {
    var a=c.ability;double slow=a.effect("slow");
    if(slow>0)HeroControl.slow(c.owner,t,slow,a.effect("slow_duration")>0?a.effect("slow_duration"):Math.max(2,a.duration()));
    if(a.effect("armor_reduction")>0)HeroControl.soft(c.owner,t,EntityAttributes.GENERIC_ARMOR,a.effect("armor_reduction"),a.effect("armor_duration"));
  }
  static void control(HeroRuntime.Cast c,Vec3d center,double radius,HeroControl.Kind kind,double duration) {
    for(var t:HeroWorld.near(c.owner.player,center,radius,c.cap()))HeroControl.apply(c.owner,t,kind,duration);
  }
  static void area(HeroRuntime.Cast c,Vec3d center,double radius,HeroCatalog.Phase phase,Consumer<LivingEntity> impact) {
    HeroPresentation.cue(c,"IMPACT",center,center,.3);
    for(var t:HeroWorld.near(c.owner.player,center,radius,c.cap()))if(HeroCombat.hit(c,t,phase))impact.accept(t);
  }
  static void phases(HeroRuntime.Cast c,BiConsumer<HeroCatalog.Phase,Integer> action) {
    int serial=0;
    for(var phase:c.ability.phases())if((phase.start()<0)==c.recastUsed)for(int i=0;i<phase.hits();i++) {
      var p=phase;int index=serial++;double at=phase.start()<0?0:phase.start()+i*phase.interval();
      if(at<=0)action.accept(p,index);else c.later(at,()->action.accept(p,index));
    }
  }
  static void bleed(HeroRuntime.Cast c,LivingEntity t,double coefficient,double duration) {
    if(!c.once("bleed",t.getUuid()))return;
    for(int i=1;i<=duration;i++)c.later(i,()->{if(HeroWorld.visible(c.owner.player,c.owner.player.getEyePos(),t))HeroCombat.hit(c,t,"PHYSICAL",coefficient);});
  }
  static void proc(HeroRuntime.Session s,LivingEntity target,String ability,String kind,double scale) {
    var c=new HeroRuntime.Cast(s,HeroRuntime.catalog().ability(s.hero.id()+"_"+ability),target,target.getPos());c.proc=true;
    c.later(.05,()->HeroCombat.hit(c,target,kind,scale));
  }
  static void pull(HeroRuntime.Cast c,LivingEntity t,Vec3d point,double strength) {
    if(!RpgEntityAdapters.allows(c.owner.player,t,RpgEntityAdapters.Effect.MOVEMENT))return;
    Vec3d delta=point.subtract(t.getPos());HeroWorld.velocity(t,delta.normalize().multiply(Math.min(.65,delta.length()*strength)).add(0,.1,0));
  }
  static void dash(HeroRuntime.Cast c,Vec3d destination,double range,Consumer<Vec3d> end) {
    var from=c.owner.player.getPos();var to=HeroWorld.move(c.owner.player,destination,range);HeroPresentation.trail(c,from.add(0,1,0),to.add(0,1,0));HeroPassives.dashed(c.owner);end.accept(to);
  }
  static void leap(HeroRuntime.Cast c,Vec3d destination,double seconds,double height,Consumer<Vec3d> impact) {
    var s=c.owner;var from=s.player.getPos();Vec3d delta=destination.subtract(from);
    if(delta.length()>c.ability.range())destination=from.add(delta.normalize().multiply(c.ability.range()));
    final Vec3d to=destination;int frames=Math.max(1,(int)(seconds*20));s.flag("busy",seconds);s.flag("control_immune",seconds);
    class Jump implements Runnable {int step;public void run(){
      step++;double t=step/(double)frames;var desired=from.lerp(to,t).add(0,4*height*t*(1-t),0);
      var before=s.player.getPos();var reached=HeroWorld.move(s.player,desired,Math.max(1,before.distanceTo(desired)));
      HeroPresentation.trail(c,before.add(0,.8,0),reached.add(0,.8,0));
      if(step==frames||reached.squaredDistanceTo(desired)>.2){s.power.unset("busy");s.power.unset("control_immune");impact.accept(reached);}else c.later(.05,this);
    }}new Jump().run();HeroPassives.dashed(s);
  }
  static void projectile(HeroRuntime.Cast c,Vec3d origin,Vec3d destination,LivingEntity homing,double speed,double radius,HeroCatalog.Phase phase,Consumer<LivingEntity> impact) {
    int id=++c.projectileCount;
    class Flight implements Runnable {Vec3d pos=origin,direction=destination.subtract(origin).normalize();int age;
      public void run(){if(age++>=40)return;
        Vec3d goal=homing!=null&&HeroWorld.legal(c.owner.player,homing)?new Vec3d(homing.getX(),homing.getBodyY(.5),homing.getZ()):destination;
        if(homing!=null)direction=direction.multiply(.7).add(goal.subtract(pos).normalize().multiply(.3)).normalize();
        double remaining=pos.distanceTo(goal);Vec3d next=pos.add(direction.multiply(Math.min(speed,remaining)));
        var clipped=HeroWorld.rayEnd(c.owner.player,pos,next);var targets=HeroWorld.line(c.owner.player,pos,clipped,.22,1);
        boolean hit=!targets.isEmpty();if(hit)clipped=targets.get(0).getPos().add(0,.8,0);
        HeroPresentation.trail(c,pos,clipped);
        if(hit||clipped.squaredDistanceTo(next)>.01||remaining<=speed) {
          var at=clipped;HeroPresentation.cue(c,"IMPACT",at,at,.25);
          var victims=radius>0?HeroWorld.near(c.owner.player,at,radius,c.cap()):targets;
          for(var t:victims)if(c.once("projectile:"+id,t.getUuid())&&HeroCombat.hit(c,t,phase))impact.accept(t);
          return;
        }
        pos=next;c.later(.05,this);
      }
    }new Flight().run();
  }
  static void beam(HeroRuntime.Cast c,Vec3d from,Vec3d to,double width,HeroCatalog.Phase phase,Consumer<LivingEntity> impact) {
    var end=HeroWorld.rayEnd(c.owner.player,from,to);HeroPresentation.trail(c,from,end);HeroPresentation.cue(c,"BEAM",from,end,.25);
    for(var t:HeroWorld.line(c.owner.player,from,end,width,c.cap()))if(HeroCombat.hit(c,t,phase))impact.accept(t);
  }
  static void zone(HeroRuntime.Cast c,Vec3d at,double radius,double duration,double interval,Consumer<LivingEntity> pulse) {
    int frames=Math.max(1,(int)Math.ceil(duration/interval));
    for(int i=0;i<frames;i++){double time=i*interval;Runnable task=()->{HeroPresentation.ring(c,at,radius);for(var t:HeroWorld.near(c.owner.player,at,radius,c.cap()))pulse.accept(t);};if(i==0)task.run();else c.later(time,task);}
  }
  static void intercept(HeroRuntime.Cast c,Vec3d at,double radius,boolean reflect) {
    var projectiles=c.owner.world.getEntitiesByClass(ProjectileEntity.class,new Box(at,at).expand(radius),p->p.getOwner()!=c.owner.player).stream().limit(24).toList();
    for(var p:projectiles){if(p.getOwner() instanceof LivingEntity owner&&!HeroWorld.legal(c.owner.player,owner))continue;
      if(reflect){p.setVelocity(p.getVelocity().multiply(-1));p.velocityModified=true;p.setOwner(c.owner.player);HeroCombat.reflected(c,p);}else p.discard();}
  }
  static ArmorStandEntity actor(HeroRuntime.Cast c,Vec3d at,double duration) {
    var actor=new ArmorStandEntity(c.owner.world,at.x,at.y,at.z);actor.setNoGravity(true);actor.setInvulnerable(true);actor.setSilent(true);actor.setInvisible(true);
    for(var slot:net.minecraft.entity.EquipmentSlot.values())if(slot.isArmorSlot())actor.equipStack(slot,c.owner.player.getEquippedStack(slot).copy());
    actor.setYaw(c.owner.player.getYaw());c.owner.world.spawnEntity(actor);
    Runnable cleanup=actor::discard;c.owner.cleanup.add(cleanup);
    c.later(duration,()->{actor.discard();c.owner.cleanup.remove(cleanup);});return actor;
  }
  static void portal(HeroRuntime.Cast c) {
    var a=c.origin;var b=c.point;var cooldown=new HashMap<UUID,Long>();
    class Portal implements Runnable {int ticks;public void run(){
      if(ticks++>=c.ability.duration()*10)return;HeroPresentation.ring(c,a,1);HeroPresentation.ring(c,b,1);
      for(var center:List.of(a,b))for(var t:c.owner.world.getEntitiesByClass(LivingEntity.class,new Box(center,center).expand(1,2,1),e->e.isAlive()&&!e.isSpectator()&&!(e instanceof ArmorStandEntity)).stream().limit(c.cap()).toList()) {
        if(cooldown.getOrDefault(t.getUuid(),0L)>HeroRuntime.tick)continue;
        cooldown.put(t.getUuid(),HeroRuntime.tick+20);
        if(HeroWorld.legal(c.owner.player,t)){if(c.once("portal",t.getUuid())){HeroCombat.hit(c,t,c.ability.phases().get(0));HeroControl.apply(c.owner,t,HeroControl.Kind.STUN,c.ability.effect("stun"));}}
        else if(t instanceof ServerPlayerEntity p&&(p==c.owner.player||p.isTeammate(c.owner.player))) {
          var destination=center==a?b:a;if(HeroWorld.safe(p,destination,true))HeroWorld.teleport(p,destination);
        }
      }c.later(.1,this);
    }}new Portal().run();
  }
}
