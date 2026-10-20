package vn.svframe.fantasyhub.core.hero;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import vn.svframe.svframelib.entity.RpgEntityAdapters;

final class SpiderSkills {
  static void cast(HeroRuntime.Cast c) {
    var s=c.owner;var p=s.player;var a=c.ability;
    switch(a.key()) {
      case "WEB_BLITZ" -> {
        var targets=HeroWorld.near(p,p.getPos(),a.range(),(int)a.effect("max_targets"));int i=0;
        for(var t:targets) {double delay=i++*.15;c.later(delay,()->{if(!HeroWorld.legal(p,t))return;
          Vec3d to=t.getPos().subtract(t.getPos().subtract(p.getPos()).normalize().multiply(1.1));
          HeroActions.dash(c,to,a.range(),at->{if(t.squaredDistanceTo(at)<6&&HeroWorld.visible(p,p.getEyePos(),t)&&HeroCombat.hit(c,t,a.phases().get(0))) {
            HeroActions.bleed(c,t,a.effect("bleed"),a.effect("bleed_duration"));HeroActions.debuffs(c,t);
          }});
        });}
      }
      case "WEB_SWING" -> HeroActions.leap(c,c.point,a.duration(),3,at->HeroActions.area(c,at,a.radius(),a.phases().get(0),t->HeroWorld.push(p,t,at,0,a.effect("knockup"))));
      case "WEB_SHOOTER_BARRAGE" -> {
        var stacks=new HashMap<UUID,Integer>();
        HeroActions.phases(c,(phase,index)->HeroActions.projectile(c,p.getEyePos(),HeroWorld.point(p,a.range()),null,1.5,0,phase,t->{
          int n=Math.min((int)a.effect("max_stacks"),stacks.merge(t.getUuid(),1,Integer::sum));
          HeroControl.slow(s,t,n*a.effect("slow_stack"),3);HeroControl.soft(s,t,EntityAttributes.GENERIC_ATTACK_SPEED,n*a.effect("slow_stack"),3);
          HeroWorld.push(p,t,p.getPos(),.4,0);if(index==phase.hits()-1)HeroActions.effect(c,t,StatusEffects.BLINDNESS,a.effect("blindness"),0);
        }));
      }
      case "COCOON_SLAM" -> {
        if(!HeroWorld.legal(p,c.target)||!HeroWorld.visible(p,p.getEyePos(),c.target)||p.distanceTo(c.target)>a.range())return;
        if(c.recastUsed)HeroActions.area(c,c.target.getPos(),a.radius(),a.phases().get(1),t->HeroWorld.push(p,t,c.target.getPos(),1,.2));
        else {HeroCombat.hit(c,c.target,a.phases().get(0));HeroControl.apply(s,c.target,HeroControl.Kind.STUN,a.effect("stun"));HeroPresentation.ring(c,c.target.getPos(),.7);}
      }
      case "AIR_COMBO" -> {
        var t=c.target;if(!HeroWorld.legal(p,t))return;
        Vec3d raised=t.getPos().add(0,1.8,0);
        if(RpgEntityAdapters.allows(p,t,RpgEntityAdapters.Effect.MOVEMENT)&&s.world.isSpaceEmpty(t,t.getBoundingBox().offset(0,1.8,0))) {
          if(t instanceof net.minecraft.server.network.ServerPlayerEntity player)HeroWorld.teleport(player,raised);else t.setPosition(raised);
        }
        HeroActions.dash(c,t.getPos().subtract(HeroWorld.forward(p).multiply(1.2)),a.range()+2,at->{});
        HeroControl.apply(s,t,HeroControl.Kind.AIR_LOCK,a.effect("air_lock"));s.flag("busy",a.duration());s.flag("control_immune",a.duration());
        var anchor=p.getPos();
        for(int i=0;i<(int)(a.duration()*20);i++)c.later(i*.05,()->{if(!HeroControl.mobilityLocked(p)){HeroWorld.teleport(p,anchor);HeroWorld.velocity(p,Vec3d.ZERO);}});
        HeroActions.phases(c,(phase,index)->{if(HeroWorld.legal(p,t)&&p.distanceTo(t)<5&&HeroWorld.visible(p,p.getEyePos(),t))HeroCombat.hit(c,t,phase);});
      }
      case "WEB_SHIELD" -> {s.flag("web_shield",a.duration());s.power.unset("web_countered");HeroPresentation.ring(c,p.getPos(),1);}
      case "WORLD_WEB_TRAP" -> {
        var center=c.target!=null?c.target.getPos():c.point;
        for(var t:HeroWorld.near(p,center,a.radius(),c.cap())){HeroControl.apply(s,t,HeroControl.Kind.ROOT,a.effect("root"));HeroActions.effect(c,t,StatusEffects.BLINDNESS,a.effect("blindness"),0);}
        HeroPresentation.ring(c,center,a.radius());
        HeroActions.phases(c,(phase,index)->HeroActions.area(c,center,a.radius(),phase,t->{if(phase.kind().startsWith("TRUE"))HeroWorld.push(p,t,center,a.effect("knockback"),.3);}));
      }
      default -> throw new IllegalArgumentException(a.id());
    }
  }
}
