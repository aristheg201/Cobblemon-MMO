package vn.svframe.fantasyhub.core.hero;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

final class SorcererSkills {
  static void cast(HeroRuntime.Cast c) {
    var s=c.owner;var p=s.player;var a=c.ability;
    switch(a.key()) {
      case "ELDRITCH_WHIP" -> {
        if(!HeroWorld.legal(p,c.target)||p.distanceTo(c.target)>a.range()||!HeroWorld.visible(p,p.getEyePos(),c.target))return;
        HeroPresentation.cue(c,"BEAM",p.getEyePos(),c.target.getEyePos(),.8);
        if(c.recastUsed){HeroActions.pull(c,c.target,p.getPos(),.4);HeroActions.debuffs(c,c.target);}else HeroCombat.hit(c,c.target,a.phases().get(0));
      }
      case "TAO_MANDALAS" -> {s.flag("mandalas",a.duration());HeroPresentation.ring(c,p.getPos().add(HeroWorld.forward(p)),1);}
      case "MIRROR_DIMENSION" -> {
        var center=c.target!=null?c.target.getPos():c.point;
        HeroActions.zone(c,center,a.radius(),a.duration(),.25,t->HeroControl.apply(s,t,HeroControl.Kind.MOBILITY_LOCK,.35));
        for(int i=0;i<a.duration()*4;i++)c.later(i*.25,()->HeroActions.intercept(c,center,a.radius(),true));
        HeroActions.phases(c,(phase,index)->HeroActions.area(c,center,a.radius(),phase,t->{}));
      }
      case "ASTRAL_PROJECTION" -> {
        s.returnAnchor=p.getPos();s.flag("astral",a.duration());s.flag("busy",a.duration());HeroActions.actor(c,s.returnAnchor,a.duration());
        HeroActions.effect(p,StatusEffects.INVISIBILITY,a.duration(),0);HeroFlight.claim(s,.12f);
      }
      case "SLING_RING" -> HeroActions.portal(c);
      case "IMAGES_OF_IKONN" -> {
        var center=c.target!=null?c.target.getPos():c.point;
        for(int i=0;i<(int)a.effect("clones");i++){double angle=2*Math.PI*i/a.effect("clones");HeroActions.actor(c,center.add(Math.cos(angle)*a.radius(),0,Math.sin(angle)*a.radius()),2.5);}
        HeroActions.area(c,center,a.radius(),a.phases().get(0),t->{HeroControl.apply(s,t,HeroControl.Kind.SILENCE,a.effect("silence"));HeroControl.apply(s,t,HeroControl.Kind.ROOT,a.effect("root"));});
      }
      case "EYE_OF_AGAMOTTO" -> {
        var center=c.target!=null?c.target.getPos():c.point;HeroPresentation.ring(c,center,a.radius());
        HeroActions.control(c,center,a.radius(),HeroControl.Kind.FREEZE,a.effect("freeze"));
        HeroActions.phases(c,(phase,index)->HeroActions.area(c,center,a.radius(),phase,t->{if(phase.kind().startsWith("TRUE"))HeroWorld.push(p,t,center,0,a.effect("knockup"));}));
      }
      default -> throw new IllegalArgumentException(a.id());
    }
  }
}
