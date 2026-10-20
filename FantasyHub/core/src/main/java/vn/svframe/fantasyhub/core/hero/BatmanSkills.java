package vn.svframe.fantasyhub.core.hero;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Box;

final class BatmanSkills {
  static void cast(HeroRuntime.Cast c) {
    var s=c.owner;var p=s.player;var a=c.ability;
    switch(a.key()) {
      case "BATARANG" -> HeroActions.phases(c,(phase,index)->HeroActions.projectile(c,p.getEyePos(),c.target!=null?c.target.getEyePos():HeroWorld.point(p,a.range()),null,1.8,c.recastUsed?a.radius():0,phase,t->{
        HeroControl.slow(s,t,a.effect("slow"),3);if(c.recastUsed)HeroControl.apply(s,t,HeroControl.Kind.SILENCE,.5);
      }));
      case "BATCLAW" -> {
        if(c.target!=null){HeroPresentation.trail(c,p.getEyePos(),c.target.getEyePos());if(HeroCombat.hit(c,c.target,a.phases().get(0))) {HeroActions.pull(c,c.target,p.getPos(),.5);HeroControl.apply(s,c.target,HeroControl.Kind.STUN,a.effect("stun"));HeroActions.debuffs(c,c.target);}}
        else HeroActions.dash(c,c.point,a.range(),at->{});
      }
      case "SMOKE_TAKEDOWN" -> {
        HeroActions.effect(p,StatusEffects.INVISIBILITY,a.duration(),0);s.flag("stealth_crit",a.duration());
        for(var t:HeroWorld.near(p,p.getPos(),a.radius(),c.cap())){HeroActions.effect(c,t,StatusEffects.BLINDNESS,a.effect("blindness"),0);HeroControl.apply(s,t,HeroControl.Kind.SILENCE,a.effect("silence"));}
        HeroPresentation.ring(c,p.getPos(),a.radius());
      }
      case "DETECTIVE_VISION" -> {
        // This one radar intentionally sees through walls; combat hits still require collision/LOS.
        for(var t:s.world.getEntitiesByClass(LivingEntity.class,p.getBoundingBox().expand(a.radius()),e->HeroWorld.legal(p,e)&&p.squaredDistanceTo(e)<=a.radius()*a.radius()).stream().limit(c.cap()).toList()) {
          HeroActions.effect(c,t,StatusEffects.GLOWING,a.effect("glowing"),0);HeroCombat.mark(s,t,a.duration(),a.effect("vulnerability"));
        }
      }
      case "DISRUPTOR" -> {HeroCombat.shields(c.target);HeroControl.apply(s,c.target,HeroControl.Kind.SILENCE,a.effect("silence"));HeroPresentation.trail(c,p.getEyePos(),c.target.getEyePos());}
      case "CAPE_COMBO" -> {
        for(var t:HeroWorld.cone(p,p.getPos(),a.range(),a.effect("cone"),c.cap()))HeroControl.apply(s,t,HeroControl.Kind.STUN,a.effect("stun"));
        HeroActions.phases(c,(phase,index)->{for(var t:HeroWorld.cone(p,p.getPos(),a.range(),a.effect("cone"),c.cap()))if(HeroCombat.hit(c,t,phase)&&index==phase.hits()-1)HeroWorld.push(p,t,p.getPos(),0,a.effect("knockup"));});
      }
      case "BATWING_STRIKE" -> {
        var center=c.target!=null?c.target.getPos():c.point;HeroPresentation.ring(c,center,a.radius());
        for(var t:HeroWorld.near(p,center,a.radius(),c.cap())){HeroControl.slow(s,t,a.effect("slow"),2);HeroControl.apply(s,t,HeroControl.Kind.SILENCE,a.effect("silence"));}
        c.later(a.phases().get(0).start(),()->HeroActions.area(c,center,a.radius(),a.phases().get(0),t->{HeroWorld.push(p,t,center,a.effect("knockback"),.35);HeroControl.apply(s,t,HeroControl.Kind.STUN,a.effect("stun"));}));
      }
      default -> throw new IllegalArgumentException(a.id());
    }
  }
}
