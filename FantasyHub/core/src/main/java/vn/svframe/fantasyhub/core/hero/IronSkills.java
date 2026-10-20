package vn.svframe.fantasyhub.core.hero;
import java.util.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

final class IronSkills {
  static void cast(HeroRuntime.Cast c) {
    var s=c.owner;var p=s.player;var a=c.ability;
    switch(a.key()) {
      case "REPULSOR_BLAST" -> {
        HeroActions.projectile(c,p.getEyePos(),c.point,null,3,0,a.phases().get(0),t->HeroWorld.push(p,t,p.getPos(),a.effect("knockback"),.12));
        if(s.active("flight"))HeroActions.dash(c,p.getPos().subtract(HeroWorld.forward(p).multiply(a.effect("flight_recoil"))),a.effect("flight_recoil"),at->{});
      }
      case "MICRO_MISSILES" -> {
        var center=c.target!=null?c.target.getPos():c.point;
        HeroActions.phases(c,(phase,index)->{
          var targets=HeroWorld.near(p,center,a.radius(),c.cap());var target=targets.isEmpty()?null:targets.get(index%targets.size());
          Vec3d origin=p.getEyePos().add(Math.cos(index)*.6,.3,Math.sin(index)*.6);
          HeroActions.projectile(c,origin,center.add(0,.5,0),target,1.4,1.5,phase,t->HeroActions.debuffs(c,t));
        });
      }
      case "TARGETING_LASERS" -> {
        var victims=new ArrayList<net.minecraft.entity.LivingEntity>();var phase=a.phases().get(0);
        Vec3d facing=p.getRotationVec(1),right=HeroWorld.forward(p).crossProduct(new Vec3d(0,1,0));
        var from=p.getEyePos();var end=HeroWorld.rayEnd(p,from,from.add(facing.multiply(a.range())));
        HeroActions.beam(c,from.add(right.multiply(.4)),end.subtract(right.multiply(.4)),.4,phase,t->{victims.add(t);HeroActions.debuffs(c,t);});
        HeroPresentation.cue(c,"BEAM",from.subtract(right.multiply(.4)),end.add(right.multiply(.4)),.45);
        var burn=a.phases().get(1);for(int i=0;i<burn.hits();i++)c.later(burn.start()+i*burn.interval(),()->{for(var t:victims)if(HeroWorld.visible(p,p.getEyePos(),t))HeroCombat.hit(c,t,burn);});
      }
      case "THRUSTER_DASH" -> {
        p.removeStatusEffect(StatusEffects.SLOWNESS);HeroControl.cleanseSlow(p);
        HeroActions.dash(c,p.getPos().add(p.getRotationVec(1).multiply(a.range())),a.range(),at->{});
        s.flag("dash_shield",a.duration());HeroActions.shield(c,p,a.effect("shield"),a.duration());
      }
      case "UNIBEAM" -> {
        s.flag("busy",a.castTime());c.later(a.castTime(),()->{
          Vec3d from=p.getPos().add(0,1.25,0),end=from.add(p.getRotationVec(1).multiply(a.range()));
          HeroActions.beam(c,from,end,.7,a.phases().get(0),t->{Vec3d right=HeroWorld.forward(p).crossProduct(new Vec3d(0,1,0));if(t.getPos().subtract(p.getPos()).dotProduct(right)<0)right=right.multiply(-1);HeroWorld.push(p,t,t.getPos().subtract(right),3,.2);});
        });
      }
      case "HOUSE_PARTY" -> {
        s.flag("drones",a.duration());var actors=List.of(HeroActions.actor(c,p.getPos().add(1.5,1,0),a.duration()+.2),HeroActions.actor(c,p.getPos().add(-1.5,1,0),a.duration()+.2));
        class Orbit implements Runnable{int step;public void run(){if(step++>=a.duration()*10)return;for(int i=0;i<2;i++){double angle=step*.15+i*Math.PI;var at=p.getPos().add(Math.cos(angle)*1.5,1.5,Math.sin(angle)*1.5);actors.get(i).setPosition(at);}c.later(.1,this);}}new Orbit().run();
        int pi=0;for(var phase:a.phases()){int drone=pi++;for(int i=0;i<phase.hits();i++)c.later(phase.start()+i*phase.interval(),()->{
          var target=HeroWorld.near(p,p.getPos(),a.range(),1).stream().findFirst().orElse(null);
          if(target!=null)HeroActions.projectile(c,actors.get(drone).getPos().add(0,.5,0),target.getEyePos(),target,2,0,phase,t->{});
        });}
      }
      case "ORBITAL_STRIKE" -> {
        var center=c.target!=null?c.target.getPos():c.point;HeroPresentation.ring(c,center,a.radius());HeroActions.control(c,center,a.radius(),HeroControl.Kind.ROOT,a.effect("root"));HeroActions.control(c,center,a.radius(),HeroControl.Kind.SILENCE,a.effect("silence"));
        c.later(2,()->{
          HeroPresentation.cue(c,"BEAM",center.add(0,25,0),center,1.3);
          HeroPresentation.cue(c,"IMPACT",center,center,1.2);
          for(var t:HeroWorld.near(p,center,a.radius(),c.cap())){HeroCombat.shields(t);HeroControl.apply(s,t,HeroControl.Kind.SILENCE,a.effect("emp"));HeroCombat.hit(c,t,a.phases().get(0));}
        });
      }
      default -> throw new IllegalArgumentException(a.id());
    }
  }
}
