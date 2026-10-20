package vn.svframe.fantasyhub.core.hero;
import java.util.*;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

final class FlashSkills {
  static void cast(HeroRuntime.Cast c) {
    var s=c.owner;var p=s.player;var a=c.ability;
    switch(a.key()) {
      case "SUPER_SPEED" -> {if(s.active("super_speed"))s.power.unset("super_speed");else s.flag("super_speed",0);HeroPassives.refresh(s);}
      case "SLOW_MOTION" -> {
        for(var t:HeroWorld.near(p,p.getPos(),a.radius(),c.cap())){HeroControl.slow(s,t,a.effect("slow"),a.duration());HeroActions.effect(c,t,StatusEffects.MINING_FATIGUE,a.duration(),(int)a.effect("mining_fatigue"));}
        HeroPresentation.ring(c,p.getPos(),a.radius());
      }
      case "LIGHTNING_THROW" -> {
        var primary=c.target;HeroActions.projectile(c,p.getEyePos(),primary.getEyePos(),null,3,0,a.phases().get(0),t->{
          HeroControl.apply(s,t,HeroControl.Kind.STUN,a.effect("stun"));int hit=0;
          for(var other:HeroWorld.near(p,t.getPos(),4,(int)a.effect("chain_targets")+1))if(other!=t&&hit++<a.effect("chain_targets")){HeroPresentation.cue(c,"BEAM",t.getEyePos(),other.getEyePos(),.3);HeroCombat.hit(c,other,a.phases().get(1));}
        });
      }
      case "WIND_FUNNEL" -> HeroActions.phases(c,(phase,index)->{
        HeroPresentation.ring(c,p.getPos(),a.radius());
        HeroActions.area(c,p.getPos(),a.radius(),phase,t->{HeroActions.pull(c,t,p.getPos(),.2);if(index==phase.hits()-1)HeroWorld.push(p,t,p.getPos(),0,a.effect("knockup"));});
      });
      case "MOLECULAR_PHASING" -> {s.lastSafe=p.getPos();s.flag("phase",a.duration());p.noClip=true;}
      case "SPEED_SIPHON" -> {
        HeroActions.cleanse(p);var t=HeroWorld.near(p,p.getPos(),a.range(),1).stream().findFirst().orElse(null);
        if(t!=null){HeroControl.slow(s,t,a.effect("speed_steal"),a.duration());s.flag("siphon",a.duration());HeroPassives.refresh(s);HeroPresentation.trail(c,t.getEyePos(),p.getEyePos());}
      }
      case "INFINITE_MASS_PUNCH" -> {
        var center=c.target!=null?c.target.getPos():c.point;HeroPresentation.ring(c,center,a.radius());
        for(var t:HeroWorld.near(p,center,a.radius(),c.cap()))HeroControl.slow(s,t,a.effect("slow"),a.duration());
        c.later(1.5,()->HeroActions.control(c,center,a.radius(),HeroControl.Kind.FREEZE,a.effect("freeze")));
        c.later(a.phases().get(0).start(),()->HeroActions.dash(c,center,a.range(),at->HeroActions.area(c,at,a.radius(),a.phases().get(0),t->HeroWorld.push(p,t,at,a.effect("knockback"),.25))));
      }
      default -> throw new IllegalArgumentException(a.id());
    }
  }
}
