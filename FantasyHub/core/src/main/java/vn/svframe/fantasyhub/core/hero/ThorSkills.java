package vn.svframe.fantasyhub.core.hero;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.*;
import vn.svframe.svframelib.entity.RpgEntityAdapters;

final class ThorSkills {
  static void cast(HeroRuntime.Cast c) {
    var s=c.owner;var p=s.player;var a=c.ability;
    switch(a.key()) {
      case "STORMBREAKER_THROW" -> HeroAxeFlight.cast(c);
      case "MJOLNIR_THROW" -> {
        Vec3d origin=p.getEyePos(),end=HeroWorld.rayEnd(p,origin,c.target!=null?c.target.getEyePos():c.point);
        double travel=Math.clamp(origin.distanceTo(end)/24,.25,.55);
        HeroPresentation.cue(c,"WEAPON_THROW",origin,end,travel);
        c.later(travel,()->{
          HeroActions.beam(c,origin,end,.5,a.phases().get(0),t->{});
          HeroPresentation.cue(c,"IMPACT",end,end,.65);
          for(var t:HeroWorld.near(p,end,a.radius(),c.cap()))HeroControl.apply(s,t,HeroControl.Kind.STUN,a.effect("stun"));
        });
        c.later(a.phases().get(1).start(),()->{
          HeroPresentation.cue(c,"WEAPON_THROW",end,p.getEyePos(),.5);
          c.later(.5,()->HeroActions.beam(c,end,p.getEyePos(),.5,a.phases().get(1),t->HeroControl.slow(s,t,a.effect("return_slow"),2)));
        });
      }
      case "THUNDEROUS_SLAM" -> HeroActions.leap(c,c.target!=null?c.target.getPos():c.point,.8,3,at->HeroActions.area(c,at,a.radius(),a.phases().get(0),t->HeroWorld.push(p,t,at,0,a.effect("knockup"))));
      case "LIGHTNING_SPIN" -> {
        s.flag("spin",a.duration());HeroPassives.refresh(s);
        HeroActions.phases(c,(phase,index)->HeroActions.area(c,p.getPos(),3,phase,t->{}));
        for(int i=0;i<a.duration()*10;i++)c.later(i*.1,()->{HeroActions.intercept(c,p.getPos(),3,false);HeroPresentation.ring(c,p.getPos(),3);});
      }
      case "STORMBREAKER" -> {
        var from=p.getEyePos();var end=HeroWorld.rayEnd(p,from,c.point);HeroActions.beam(c,from,end,.9,a.phases().get(0),t->{});
        var phase=a.phases().get(1);
        for(int i=0;i<phase.hits();i++)c.later(phase.start()+i*phase.interval(),()->{
          HeroPresentation.trail(c,from,end);for(var t:HeroWorld.line(p,from,end,1,c.cap()))HeroCombat.hit(c,t,phase);
        });
      }
      case "BIFROST" -> {
        var center=c.target!=null?c.target.getPos():c.point;HeroPresentation.trail(c,center.add(0,25,0),center);
        HeroActions.area(c,center,a.radius(),a.phases().get(0),t->HeroWorld.push(p,t,center,a.effect("knockback"),.25));
        for(var t:s.world.getEntitiesByClass(LivingEntity.class,new Box(center,center).expand(a.radius()),e->e.isAlive()&&(e==p||e.isTeammate(p))).stream().limit(c.cap()).toList()) {
          HeroActions.cleanse(t);HeroActions.shield(c,t,a.effect("shield"),5);
        }
      }
      case "GOD_OF_THUNDER" -> {s.flag("awakened",a.duration());HeroPassives.refresh(s);HeroPresentation.ring(c,p.getPos(),2);}
      case "BRING_ME_THANOS" -> {
        var center=c.target!=null?c.target.getPos():c.point;
        for(var t:HeroWorld.near(p,center,a.radius(),c.cap())){HeroControl.apply(s,t,HeroControl.Kind.SILENCE,a.effect("silence"));HeroControl.slow(s,t,a.effect("slow"),2);}
        HeroPresentation.ring(c,center,a.radius());
        HeroActions.leap(c,center,2,5,at->HeroActions.area(c,at,a.radius(),a.phases().get(0),t->HeroWorld.push(p,t,at,3,.5)));
        var phase=a.phases().get(1);for(int i=0;i<phase.hits();i++)c.later(phase.start()+i*phase.interval(),()->{
          HeroPresentation.trail(c,center.add(0,20,0),center);HeroActions.area(c,center,a.radius(),phase,t->{});
        });
      }
      default -> throw new IllegalArgumentException(a.id());
    }
  }
}
