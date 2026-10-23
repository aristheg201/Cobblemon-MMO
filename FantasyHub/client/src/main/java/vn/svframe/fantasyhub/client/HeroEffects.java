package vn.svframe.fantasyhub.client;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Bounded emissive skill geometry: charge, travel, contact, impact and sustained states. */
final class HeroEffects {
 private static final Identifier WHITE=Identifier.ofVanilla("textures/misc/white.png");
 private static final Map<String,Long> NATIVE=new TreeMap<>(),RENDERED=new TreeMap<>();
 private static final double[][] BAT_POINTS={{-1,0},{-.6,.4},{-.25,.15},{-.1,.45},{0,.3},{.1,.45},{.25,.15},{.6,.4},{1,0},{.45,-.15},{.3,-.4},{0,-.2},{-.3,-.4},{-.45,-.15}};
 private static final Vec3d UP=new Vec3d(0,1,0),X=new Vec3d(1,0,0),Z=new Vec3d(0,0,1);
 private static long burstTick=-1,dropped;
 private static int emitted,active,frameVertices,vertexLimit;
 private static long geometryBudgetDrops;
 private HeroEffects(){}
 static void clear(){NATIVE.clear();RENDERED.clear();burstTick=-1;emitted=0;dropped=0;active=0;frameVertices=0;geometryBudgetDrops=0;HeroMotionEchoes.clear();}
 static void burst(HeroVisuals.Cue c,long now,int quality){
  if(FantasyHubClient.options!=null&&!FantasyHubClient.options.cobblemonEffects||HeroVisuals.density()<=0)return;
  var mc=MinecraftClient.getInstance();if(mc.player==null||mc.world==null||mc.player.squaredDistanceTo(c.from())>(quality==0?256:1600))return;
  if(!c.phase().equals("CAST")&&!c.phase().equals("IMPACT"))return;
  if(burstTick!=now){burstTick=now;emitted=0;}if(emitted>=(quality==0?1:quality==1?3:5)){dropped++;return;}
  boolean hit=c.phase().equals("IMPACT");String effect=switch(c.hero()){
   case "SORCERER_SUPREME"->hit?"aerialace_targetburst":"psybeam_charge";
   case "IRON_MAN"->hit?"explosion_burst_flash":"metal_sparks";
   case "THOR"->"thunderbolt_actorbolts";
   case "FLASH","SPIDER_MAN"->hit?"aerialace_targetburst":"quickattack_dashlines";
   default->"";
  };
  if(c.hero().equals("BATMAN")&&c.ability().equals("SMOKE_TAKEDOWN")&&!hit){
   emitted++;
   for(int i=0;i<(quality==0?12:28);i++){double a=i*2.4,r=.3+(i%5)*.3;mc.world.addParticle(net.minecraft.particle.ParticleTypes.LARGE_SMOKE,c.from().x+Math.cos(a)*r,c.from().y+.25+(i%3)*.3,c.from().z+Math.sin(a)*r,Math.cos(a)*.018,.035,Math.sin(a)*.018);}
  }
  if(effect.isEmpty())return;emitted++;
  if(KabutoSnowstorm.emit(effect,c.from().add(0,hit?.5:1,0),c.to().subtract(c.from()).normalize()))NATIVE.merge(c.hero()+":"+effect,1L,Long::sum);
 }
 static com.google.gson.JsonObject observation(){var d=new com.google.gson.JsonObject();var n=new com.google.gson.JsonObject();NATIVE.forEach(n::addProperty);d.add("nativeEffects",n);var g=new com.google.gson.JsonObject();RENDERED.forEach(g::addProperty);d.add("geometryFrames",g);d.addProperty("budgetDrops",dropped);d.addProperty("activeCues",active);d.addProperty("mjolnirFrames",HeroMjolnir.renders());d.addProperty("frameVertices",frameVertices);d.addProperty("vertexLimit",vertexLimit);d.addProperty("geometryBudgetDrops",geometryBudgetDrops);d.addProperty("motionEchoFrames",HeroMotionEchoes.frames());return d;}
 static void render(WorldRenderContext context,Iterable<HeroVisuals.Cue> effects,Map<Integer,HeroVisuals.State> states,long tick,int quality){
  if(HeroVisuals.density()<=0||context.matrixStack()==null||context.consumers()==null)return;
  var m=context.matrixStack();var camera=context.camera().getPos();double now=tick+context.tickCounter().getTickDelta(false);
  m.push();m.translate(-camera.x,-camera.y,-camera.z);active=0;frameVertices=0;vertexLimit=quality==0?24000:quality==1?56000:88000;
  for(var c:effects){
   if(c.phase().equals("AXE")||c.phase().equals("TETHER"))continue;
   if(frameVertices>=vertexLimit){geometryBudgetDrops++;break;}
   if(c.from().squaredDistanceTo(camera)>(quality==0?1600:4096))continue;active++;
   var v=context.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
   double age=now-c.start(),progress=Math.clamp(age/Math.max(1,c.expires()-c.start()),0,1),fade=Math.min(1,Math.max(0,(c.expires()-now)/5));
   int color=HeroVisuals.color(c.hero());var actor=actor(c,context);var forward=c.to().subtract(c.from()).normalize();if(forward.lengthSquared()<.01)forward=Z;
   var right=UP.crossProduct(forward).normalize();if(right.lengthSquared()<.01)right=X;
   RENDERED.merge(c.hero()+":"+c.phase(),1L,Long::sum);
   atmosphere(m,v,c,actor,camera,now,progress,fade,quality);
   if(c.phase().equals("WEAPON_THROW")){
    Vec3d at=c.from().lerp(c.to(),progress).add(0,Math.sin(progress*Math.PI)*.4,0);
    lightning(m,v,c.from().lerp(at,.65),at,camera,now,color,fade,.055,quality);
    m.push();m.translate(at.x,at.y,at.z);HeroMjolnir.render(m,context.consumers(),age*52);m.pop();continue;
   }
   if(c.phase().equals("BEAM")||c.phase().equals("TRAIL")||c.phase().equals("PROJECTILE")){
    travel(m,v,c,camera,now,progress,fade,color,quality);continue;
   }
   if(c.phase().equals("IMPACT")||c.phase().equals("HIT")){
    double r=Math.min(c.phase().equals("HIT")?1.2:Math.max(1.5,c.radius()),8)*(.15+.85*progress);
    ring(m,v,c.from().add(0,.12,0),X,Z,r,0,color,fade*(1-progress),quality);
    if(c.hero().equals("THOR")){
     lightning(m,v,c.from().add(0,14,0),c.from().add(0,.3,0),camera,now,color,fade,.11,quality);
     for(int i=0;i<(quality==0?3:6);i++){double a=i*Math.PI/3+Math.floor(now/3)*.15;lightning(m,v,c.from().add(0,.2,0),c.from().add(Math.cos(a)*r,.15,Math.sin(a)*r),camera,now+i,color,fade,.045,0);}
    }else if(c.hero().equals("IRON_MAN")){
     sphere(m,v,c.from().add(0,.6,0),r*.65,0xffab37,fade*(1-progress),quality);
     beam(m,v,c.from(),c.from().add(0,2.5*(1-progress),0),camera,.3,0xffdb8a,fade);
    }else if(c.hero().equals("SORCERER_SUPREME")){
     mandala(m,v,c.from().add(0,.14,0),X,Z,r,now*.035,color,fade,quality);
    }else if(c.hero().equals("SPIDER_MAN"))web(m,v,c.from().add(0,.15,0),r,fade*(1-progress),quality);
    else if(c.hero().equals("FLASH"))for(int i=0;i<3;i++)ring(m,v,c.from().add(0,i*.4,0),X,Z,r+i*.15,age*.12,color,fade,quality);
    continue;
   }
   boolean zone=c.phase().equals("ZONE");
   switch(c.hero()){
    case "THOR"->{
     if(c.ability().equals("BIFROST")||c.ability().equals("BRING_ME_THANOS")){
      Vec3d center=c.to();mandala(m,v,center.add(0,.12,0),X,Z,Math.max(2,c.radius()*.7),now*.02,0x86deff,fade,quality);
      if(c.ability().equals("BIFROST"))prism(m,v,center,center.add(0,24,0),camera,fade,quality);
      else ring(m,v,center.add(0,.2,0),X,Z,Math.max(2,c.radius()),0,color,fade,quality);
     }else if(c.ability().equals("LIGHTNING_SPIN")){
      for(int i=0;i<3;i++){double a=age*.4+i*Math.PI*2/3;Vec3d to=actor.add(Math.cos(a)*3,1+Math.sin(a)*.6,Math.sin(a)*3);lightning(m,v,actor.add(0,1,0),to,camera,now+i,color,fade,.07,quality);}
     }else if(c.ability().equals("GOD_OF_THUNDER"))lightning(m,v,actor.add(0,15,0),actor.add(0,1,0),camera,now,color,fade,.12,quality);
     else ring(m,v,actor.add(0,.12,0),X,Z,.8+progress,age*.2,color,fade,quality);
    }
    case "SPIDER_MAN"->{
     switch(c.ability()){
      case "WORLD_WEB_TRAP"->web(m,v,c.to().add(0,.12,0),Math.max(3,c.radius()),fade,quality);
      case "COCOON_SLAM"->cocoon(m,v,c.to(),now,fade,quality);
      case "WEB_SWING"->{var anchor=c.to().add(0,5,0);tether(m,v,actor.add(0,1.6,0),anchor,camera,now,color,fade,true);}
      case "WEB_BLITZ","AIR_COMBO"->{tether(m,v,actor.add(0,1.1,0),c.to().add(0,1,0),camera,now,color,fade,true);ring(m,v,c.to().add(0,1,0),right,UP,1.2,age*.4,color,fade,quality);}
      case "WEB_SHIELD"->sphere(m,v,actor.add(0,1,0),1.1,color,fade,quality);
      default->{ring(m,v,actor.add(0,1.2,0).add(right.multiply(.5)),right,UP,.2+progress*.2,age,color,fade,quality);}
     }
    }
    case "SORCERER_SUPREME"->{
     double r=Math.max(1.5,c.radius());Vec3d center=c.to().add(0,.15,0);
     switch(c.ability()){
      case "TAO_MANDALAS"->hands(m,v,actor,forward,right,now,color,fade,quality);
      case "SLING_RING"->{mandala(m,v,c.from().add(0,1.2,0),right,UP,1.2,now*.05,color,fade,quality);mandala(m,v,c.to().add(0,1.2,0),right,UP,1.2,-now*.05,color,fade,quality);}
      case "MIRROR_DIMENSION"->{mandala(m,v,center,X,Z,r,now*.025,color,fade,quality);shards(m,v,center,r,now,fade,quality);}
      case "IMAGES_OF_IKONN"->{mandala(m,v,center,X,Z,r,now*.025,color,fade,quality);for(int i=0;i<4;i++){double a=i*Math.PI/2+now*.015;tether(m,v,center.add(Math.cos(a)*r,1.4,Math.sin(a)*r),center.add(0,1,0),camera,now+i,0xffb24b,fade,false);}}
      case "EYE_OF_AGAMOTTO"->{mandala(m,v,center,X,Z,r,now*.02,0x56ff9c,fade,quality);sphere(m,v,center.add(0,1,0),r*.7,0x64eeb2,fade*.65,quality);}
      case "ASTRAL_PROJECTION"->{mandala(m,v,c.from().add(0,.1,0),X,Z,1.4,now*.04,0x8ce7ff,fade,quality);sphere(m,v,actor.add(0,1,0),.9,0x8ce7ff,fade*.7,quality);}
      default->hands(m,v,actor,forward,right,now,color,fade,quality);
     }
    }
    case "IRON_MAN"->{
     if(c.ability().equals("ORBITAL_STRIKE")){mandala(m,v,c.to().add(0,.15,0),X,Z,Math.max(2,c.radius()),now*.025,0xff7140,fade,quality);beam(m,v,c.to(),c.to().add(0,18,0),camera,.025,0xff7140,fade);}
     else if(c.ability().equals("UNIBEAM")){sphere(m,v,actor.add(forward.multiply(.4)).add(0,1.25,0),.18+Math.min(age/15,.6),0x9bffff,fade,quality);for(int i=0;i<3;i++)ring(m,v,actor.add(0,1.25,0).add(forward.multiply(.5)),right,UP,.3+i*.18,age*.08,color,fade,quality);}
     else if(!c.ability().equals("HOUSE_PARTY"))sphere(m,v,actor.add(0,1.3,0).add(forward.multiply(.65)),.2,color,fade,quality);
    }
    case "FLASH"->{
     if(c.ability().equals("WIND_FUNNEL")){for(int i=0;i<6;i++)ring(m,v,actor.add(0,i*.5,0),X,Z,.6+i*.3,now*.3+i,color,fade,quality);}
     else{double r=Math.max(1,c.radius());ring(m,v,c.to().add(0,.15,0),X,Z,r,age*.1,color,fade,quality);for(int i=0;i<3;i++){double a=age*.2+i*2;lightning(m,v,actor.add(0,1,0),actor.add(Math.cos(a)*1.2,1.5,Math.sin(a)*1.2),camera,now+i,color,fade,.04,0);}}
    }
    case "BATMAN"->{
     if(c.ability().equals("SMOKE_TAKEDOWN")){smoke(m,v,c.from(),camera,now,progress,fade,quality);flock(m,v,c.from(),camera,now,fade,quality);}
     else if(c.ability().equals("BATWING_STRIKE")){ring(m,v,c.to().add(0,.15,0),X,Z,Math.max(2,c.radius()),0,0xd7a944,fade,quality);batwing(m,v,c.to().add((progress-.5)*28,7,0),now,fade);}
     else ring(m,v,actor.add(0,.15,0),X,Z,c.ability().equals("DETECTIVE_VISION")?1+progress*8:1,age*.1,color,fade,quality);
    }
   }
  }
  states(m,context,states,camera,now,quality);playerEchoes(m,context,effects,now,quality);HeroMotionEchoes.render(m,context,states,now,quality);m.pop();
 }

 private static void playerEchoes(MatrixStack m,WorldRenderContext ctx,Iterable<HeroVisuals.Cue> cues,double now,int quality){
  var mc=MinecraftClient.getInstance();if(mc.world==null)return;int rendered=0;
  var seen=new HashSet<Long>();
  for(var c:cues){
   if(!c.hero().equals("SORCERER_SUPREME")||!c.phase().equals("ZONE")||!seen.add(c.cast())||c.from().squaredDistanceTo(ctx.camera().getPos())>1600)continue;
   if(!(mc.world.getEntityById(c.entity()) instanceof net.minecraft.client.network.AbstractClientPlayerEntity player))continue;
   double fade=Math.clamp((c.expires()-now)/10,0,1);float yaw=MathHelper.lerpAngleDegrees(ctx.tickCounter().getTickDelta(false),player.prevBodyYaw,player.bodyYaw);
   if(c.ability().equals("ASTRAL_PROJECTION")){
    HeroPlayerEcho.render(m,ctx,player,c.from(),yaw,fade*.85,0xffffff);
    HeroPlayerEcho.render(m,ctx,player,player.getLerpedPos(ctx.tickCounter().getTickDelta(false)),yaw,fade*.42,0xc5f5ff);
   }
   if(c.ability().equals("IMAGES_OF_IKONN"))for(int i=0;i<4&&rendered<16;i++,rendered++){
    double a=i*Math.PI/2;Vec3d at=c.to().add(Math.cos(a)*Math.max(1.5,c.radius()),0,Math.sin(a)*Math.max(1.5,c.radius()));
    HeroPlayerEcho.render(m,ctx,player,at,yaw,fade*.65,0xffffff);
   }
  }
 }

 /** Stage-specific solid surfaces make wind-up, sustained action and contact read differently. */
 private static void atmosphere(MatrixStack m,VertexConsumer v,HeroVisuals.Cue c,Vec3d actor,Vec3d camera,double now,double t,double fade,int q){
  boolean impact=c.phase().equals("IMPACT")||c.phase().equals("HIT"),cast=c.phase().equals("CAST"),zone=c.phase().equals("ZONE");
  double r=Math.clamp(c.radius(),1.5,7);Vec3d p=impact?c.from():zone?c.to():actor;
  switch(c.hero()){
   case "THOR"->{
    if(impact){
     annulus(m,v,p.add(0,.16,0),X,Z,r*t*.65,r*(.2+t),now*.01,0x8acfff,fade*(1-t)*.65,q);
     for(int i=0;i<(q==0?6:12);i++){double a=i*2.399;Vec3d tip=p.add(Math.cos(a)*r*(.4+t),.15,Math.sin(a)*r*(.4+t));lightning(m,v,p.add(0,.18,0),tip,camera,now+i,0x99dfff,fade*(1-t),.055,0);var rock=tip.add(0,Math.sin(t*Math.PI)*(i%3+.4),0);disc(m,v,rock,X,UP,.18+(i%3)*.1,0x303742,fade*(1-t),5);}
     star(m,v,p.add(0,.7,0),camera,r*.5,now,0xe8ffff,fade*(1-t),q);
    }else if(cast||zone){
     if(c.ability().equals("LIGHTNING_SPIN")){for(int i=0;i<3;i++){double a=now*.3+i*2.094;annulus(m,v,actor.add(0,.5+i*.45,0),X,Z,1.5+i*.25,1.9+i*.25,a,0x8adfff,fade*.2,q);}}
     else if(c.ability().equals("GOD_OF_THUNDER")||c.ability().equals("BRING_ME_THANOS")){for(int i=0;i<(q==0?3:6);i++){double a=i*1.047+now*.035;Vec3d foot=p.add(Math.cos(a)*r,.1,Math.sin(a)*r);lightning(m,v,foot.add(0,8+(i%3)*3,0),foot,camera,now+i,0xa5dfff,fade,.055,q);}}
     else annulus(m,v,actor.add(0,.11,0),X,Z,.65,1.1,now*.08,0x70bfff,fade*.18,q);
    }
   }
   case "SORCERER_SUPREME"->{
    int color=c.ability().equals("EYE_OF_AGAMOTTO")?0x56ffa1:0xffa52f;
    if(impact){annulus(m,v,p.add(0,.17,0),X,Z,r*t*.8,r*(.15+t),now*.04,color,fade*(1-t)*.55,q);star(m,v,p.add(0,1,0),camera,r*.65,now,color,fade*(1-t),q);}
    if(cast||zone){
     if(c.ability().equals("SLING_RING")){portal(m,v,c.from().add(0,1.25,0),camera,now,fade,q);portal(m,v,c.to().add(0,1.25,0),camera,-now,fade,q);}
     else if(c.ability().equals("TAO_MANDALAS")){star(m,v,actor.add(0,1.3,0),camera,.85,now,color,fade*.35,q);}
     else{annulus(m,v,p.add(0,.13,0),X,Z,r*.25,r,now*.03,color,fade*.11,q);for(int i=0;i<(q==0?4:8);i++){double a=i*Math.PI/4+now*.02;Vec3d b=p.add(Math.cos(a)*r*.85,0,Math.sin(a)*r*.85);beam(m,v,b,b.add(0,.7+Math.sin(now*.15+i)*.4,0),camera,.035,color,fade*.7);}}
     if(c.ability().equals("EYE_OF_AGAMOTTO")){for(int i=0;i<4;i++)annulus(m,v,p.add(0,.4+i*.65,0),X,Z,r*.6,r*.66,now*.04+i,color,fade*.3,q);}

    }
   }
   case "SPIDER_MAN"->{
    if(impact){star(m,v,p.add(0,.6,0),camera,1.2+t,now,0xe8f7ff,fade*(1-t),q);for(int i=0;i<(q==0?4:8);i++){double a=i*.785;var end=p.add(Math.cos(a)*r,.12,Math.sin(a)*r);tether(m,v,p.add(0,.3,0),end,camera,now,0xf4faff,fade*(1-t),true);}}
    if((cast||zone)&&c.ability().equals("COCOON_SLAM")){sphere(m,v,c.to().add(0,.85,0),.62,0xeefaff,fade*.9,q);for(int i=0;i<6;i++){double a=i*Math.PI/3;var to=c.to().add(Math.cos(a)*.55,1.6,Math.sin(a)*.55);tether(m,v,c.to().add(-Math.cos(a)*.4,.1,-Math.sin(a)*.4),to,camera,now+i,0xeefaff,fade,true);}}
    if((cast||zone)&&c.ability().equals("WEB_BLITZ")){for(int i=0;i<4;i++){Vec3d at=actor.lerp(c.to(),i/4d);annulus(m,v,at.add(0,1,0),X,UP,.55,.65,now*.1,0xf7ffff,fade*.3,q);}}
   }
   case "BATMAN"->{
    if(c.ability().equals("DETECTIVE_VISION")&&(cast||zone)){double scan=1+t*9;annulus(m,v,actor.add(0,.18,0),X,Z,scan-.18,scan,now*.02,0x6db5df,fade*.65,q);for(int i=0;i<4;i++){double a=i*Math.PI/2;Vec3d at=actor.add(Math.cos(a)*scan,1,Math.sin(a)*scan);beam(m,v,at.add(0,-.8,0),at.add(0,.8,0),camera,.025,0x6db5df,fade*.4);}}
    if(c.ability().equals("DISRUPTOR")&&(cast||impact)){Vec3d end=c.to().add(0,.8,0);lightning(m,v,actor.add(0,1.3,0),end,camera,now,0x7dc8ed,fade,.06,q);star(m,v,end,camera,.7,now,0xa1e5ff,fade,q);}
    if(c.ability().equals("CAPE_COMBO")){double swing=now*.35;for(int i=0;i<3;i++){double a=swing+i*.55;Vec3d x=new Vec3d(Math.cos(a),0,Math.sin(a));annulus(m,v,actor.add(0,.8+i*.25,0),x,UP,.5,1.8,0,0x243747,fade*.65,q);}if(impact)star(m,v,p.add(0,.8,0),camera,1,now,0xc9dcea,fade,q);}
    if(c.ability().equals("BATWING_STRIKE")){if(impact){smoke(m,v,p,camera,now,t,fade,q);star(m,v,p.add(0,.65,0),camera,1.4+t,now,0xffbd63,fade*(1-t),q);}else if(zone){for(int i=0;i<3;i++){double delay=(t*4-i);if(delay>0&&delay<1){var at=p.add((i-1)*2,0,0);star(m,v,at.add(0,.6,0),camera,1+delay,now,0xffc776,fade*(1-delay),q);smoke(m,v,at,camera,now,delay,fade*.6,0);}}}}
    if(impact&&!c.ability().equals("BATWING_STRIKE"))star(m,v,p.add(0,.7,0),camera,.9,now,0xb9d0dc,fade*(1-t),q);
   }
   case "FLASH"->{if(impact){annulus(m,v,p.add(0,.3,0),X,Z,r*t*.75,r*(.2+t),now*.02,0xffb51d,fade*(1-t)*.7,q);star(m,v,p.add(0,.8,0),camera,r*.6,now,0xffeaa7,fade*(1-t),q);}}
  }
 }
 private static void annulus(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d x,Vec3d y,double inner,double outer,double angle,int color,double fade,int q){
  int n=q==0?20:36;for(int i=0;i<n;i++){double a=i*Math.PI*2/n+angle,b=(i+1)*Math.PI*2/n+angle;Vec3d aa=x.multiply(Math.cos(a)).add(y.multiply(Math.sin(a))),bb=x.multiply(Math.cos(b)).add(y.multiply(Math.sin(b)));quad(m,v,p.add(aa.multiply(inner)),p.add(aa.multiply(outer)),p.add(bb.multiply(outer)),p.add(bb.multiply(inner)),alpha(color,fade));}
 }
 private static void disc(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d x,Vec3d y,double r,int color,double fade,int n){for(int i=0;i<n;i++){double a=i*Math.PI*2/n,b=(i+1)*Math.PI*2/n;quad(m,v,p,p.add(x.multiply(Math.cos(a)*r)).add(y.multiply(Math.sin(a)*r)),p.add(x.multiply(Math.cos(b)*r)).add(y.multiply(Math.sin(b)*r)),p,alpha(color,fade));}}
 private static Vec3d billboard(Vec3d p,Vec3d camera){var x=camera.subtract(p).crossProduct(UP).normalize();return x.lengthSquared()<.01?X:x;}
 private static void star(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d camera,double r,double time,int color,double fade,int q){
  Vec3d x=billboard(p,camera),y=x.crossProduct(camera.subtract(p).normalize()).normalize();int n=q==0?8:12;
  disc(m,v,p,x,y,r*.35,color,fade*.2,n);
  for(int i=0;i<n;i++){double a=i*Math.PI*2/n+time*.017;var dir=x.multiply(Math.cos(a)).add(y.multiply(Math.sin(a)));var tangent=x.multiply(-Math.sin(a)).add(y.multiply(Math.cos(a)));double len=r*(.65+.35*Math.sin(i*4.3+Math.floor(time/3)));quad(m,v,p.add(tangent.multiply(r*.06)),p.add(dir.multiply(len)),p.subtract(tangent.multiply(r*.06)),p,alpha(color,fade*.8));}
 }
 private static void smoke(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d camera,double time,double t,double fade,int q){
  for(int i=0;i<(q==0?7:15);i++){double a=i*2.399,r=.4+(i%5)*.35;Vec3d at=p.add(Math.cos(a)*r*(.6+t),.35+(i%4)*.4+t*.7,Math.sin(a)*r*(.6+t));Vec3d x=billboard(at,camera),y=x.crossProduct(camera.subtract(at).normalize()).normalize();double size=.7+(i%3)*.2+t*.5;
   for(int layer=2;layer>=0;layer--)disc(m,v,at.add(camera.subtract(at).normalize().multiply((2-layer)*.015)),x,y,size*(.7+layer*.16),layer==0?0x343e49:0x1d2631,fade*(layer==2?.1:layer==1?.22:.4),q==0?8:12);
  }
 }
 private static void flock(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d camera,double time,double fade,int q){for(int i=0;i<(q==0?4:10);i++){double a=i*2.399+time*.035;var at=p.add(Math.cos(a)*(1+i*.12),.7+(i%4)*.5+Math.sin(time*.15+i)*.3,Math.sin(a)*(1+i*.12));bat(m,v,at,billboard(at,camera),UP,.22+Math.sin(time*.7+i)*.07,0x111b25,fade);}}
 private static void portal(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d camera,double time,double fade,int q){Vec3d x=billboard(p,camera);annulus(m,v,p,x,UP,1.12,1.27,time*.04,0xffae39,fade*.65,q);disc(m,v,p,x,UP,1.1,0x31203b,fade*.42,q==0?20:32);for(int i=0;i<(q==0?12:24);i++){double a=i*6.283/(q==0?12:24)+time*.07;var dir=x.multiply(Math.cos(a)).add(UP.multiply(Math.sin(a)));var start=p.add(dir.multiply(1.23));stroke(m,v,start,start.add(dir.multiply(.1+(i%3)*.07)).add(0,-.15,0),x.crossProduct(UP),.016,0xffcf6f,fade);}}

 private static void batarang(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d dir,Vec3d side,double time,double fade){double a=time*.7;Vec3d x=side.multiply(Math.cos(a)).add(UP.multiply(Math.sin(a))),y=UP.multiply(Math.cos(a)).subtract(side.multiply(Math.sin(a)));bat(m,v,p.add(dir.multiply(.035)),x,y,.47,0xb3c3cf,fade);bat(m,v,p.subtract(dir.multiply(.035)),x,y,.47,0x25313b,fade);for(int edge=0;edge<BAT_POINTS.length;edge++){var aa=BAT_POINTS[edge];var bb=BAT_POINTS[(edge+1)%BAT_POINTS.length];var from=p.add(x.multiply(aa[0]*.47)).add(y.multiply(aa[1]*.47));var to=p.add(x.multiply(bb[0]*.47)).add(y.multiply(bb[1]*.47));var depth=dir.multiply(.035);quad(m,v,from.add(depth),from.subtract(depth),to.subtract(depth),to.add(depth),alpha(0x516b80,fade));}for(int i=0;i<3;i++)bat(m,v,p.subtract(dir.multiply(.18+i*.15)),x,y,.47,0x91c8e5,fade*(.22-i*.06));stroke(m,v,p.subtract(x.multiply(.47)),p.add(x.multiply(.47)),dir,.04,0x303c46,fade);}
 private static void batwing(MatrixStack m,VertexConsumer v,Vec3d p,double time,double fade){bat(m,v,p,X,Z,4.4,0x101923,fade);quad(m,v,p.add(-.28,0,-2.2),p.add(.28,0,-2.2),p.add(.45,.12,1.7),p.add(-.45,.12,1.7),alpha(0x263543,fade));for(int sign:new int[]{-1,1})stroke(m,v,p.add(sign*.5,.03,1),p.add(sign*3.8,.03,0),UP,.045,0x566d80,fade*.65);}
 private static Vec3d actor(HeroVisuals.Cue c,WorldRenderContext context){var mc=MinecraftClient.getInstance();var e=mc.world==null?null:mc.world.getEntityById(c.entity());return e==null?c.from():e.getLerpedPos(context.tickCounter().getTickDelta(false));}
 private static void travel(MatrixStack m,VertexConsumer v,HeroVisuals.Cue c,Vec3d camera,double now,double progress,double fade,int color,int quality){
  Vec3d a=c.from(),b=c.to();boolean shot=c.phase().equals("PROJECTILE");
  switch(c.hero()){
   case "THOR","FLASH"->lightning(m,v,a,b,camera,now,color,fade,c.hero().equals("THOR")?.085:.05,quality);
   case "SPIDER_MAN"->{tether(m,v,a,b,camera,now,color,fade,true);if(shot)sphere(m,v,b,.12,color,fade,0);}
   case "SORCERER_SUPREME"->tether(m,v,a,b,camera,now,color,fade,false);
   case "IRON_MAN"->{boolean unibeam=c.ability().equals("UNIBEAM")||c.ability().equals("ORBITAL_STRIKE");beam(m,v,a,b,camera,unibeam?.27:shot?.055:.08,color,fade);if(shot){sphere(m,v,b,.18,c.ability().equals("MICRO_MISSILES")?0xffac38:color,fade,0);}}
   case "BATMAN"->{if(c.ability().equals("BATARANG")){var direction=b.subtract(a).normalize();var side=direction.crossProduct(UP).normalize();batarang(m,v,a.lerp(b,progress),direction,side,now,fade);}else tether(m,v,a,b,camera,now,0xbac0cc,fade,true);}
  }
 }
 private static void states(MatrixStack m,WorldRenderContext ctx,Map<Integer,HeroVisuals.State> states,Vec3d camera,double now,int quality){
  var mc=MinecraftClient.getInstance();if(mc.world==null)return;
  var v=ctx.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));int n=0;
  for(var row:states.entrySet()){
   var e=mc.world.getEntityById(row.getKey());var s=row.getValue();if(e==null||e.squaredDistanceTo(camera)>(quality==0?400:1600)||n++>32)continue;
   var at=e.getLerpedPos(ctx.tickCounter().getTickDelta(false));int color=HeroVisuals.color(s.hero());
   if(s.hero().equals("THOR")&&(s.flags()&32)!=0)for(int i=0;i<(quality==0?2:4);i++){double a=now*.16+i*Math.PI/2;lightning(m,v,at.add(Math.cos(a)*.5,.4,Math.sin(a)*.5),at.add(Math.cos(a+1)*.7,1.9,Math.sin(a+1)*.7),camera,now+i,color,.8,.035,0);}
   if((s.flags()&128)!=0){var f=e.getRotationVec(1).multiply(1,0,1).normalize();hands(m,v,at,f,UP.crossProduct(f).normalize(),now,color,.9,quality);}
   if((s.flags()&64)!=0){sphere(m,v,at.add(0,1,0),1.1,color,.7,quality);}
   if(s.hero().equals("IRON_MAN")){
    if((s.flags()&512)!=0)for(int i=0;i<2;i++){double a=now*.075+i*Math.PI;var p=at.add(Math.cos(a)*1.5,2,Math.sin(a)*1.5);drone(m,v,p,now+i,color,quality);}
    if((s.flags()&1)!=0)for(int sign:new int[]{-1,1})beam(m,v,at.add(sign*.18,.15,0),at.add(sign*.18,-.65-Math.sin(now*.7)*.12,0),camera,.08,color,.8);
   }
   if(s.hero().equals("FLASH")&&e.getVelocity().horizontalLengthSquared()>.003&&(s.flags()&8)!=0){var back=e.getRotationVec(1).multiply(-1.3,0,-1.3);for(int i=0;i<2;i++)lightning(m,v,at.add(0,.5+i*.7,0),at.add(back).add(0,.4+i*.7,0),camera,now+i,color,.85,.035,0);}
  }
 }
 private static void hands(MatrixStack m,VertexConsumer v,Vec3d at,Vec3d forward,Vec3d right,double now,int color,double fade,int quality){for(int sign:new int[]{-1,1})mandala(m,v,at.add(0,1.28,0).add(forward.multiply(.7)).add(right.multiply(sign*.6)),right,UP,.52,now*.045*sign,color,fade,quality);}
 private static void prism(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d camera,double fade,int quality){int[] colors={0x84dcff,0x9da6ff,0xffb8e6,0xffffc5,0x81ffd1};for(int i=0;i<(quality==0?2:5);i++){double angle=i*Math.PI*2/5;var o=new Vec3d(Math.cos(angle)*.45,0,Math.sin(angle)*.45);beam(m,v,a.add(o),b.add(o),camera,.16,colors[i],fade*.8);}}
 private static void cocoon(MatrixStack m,VertexConsumer v,Vec3d center,double now,double fade,int quality){
  for(int i=0;i<(quality==0?8:16);i++){double h=i/(quality==0?8d:16d)*1.8;double r=.32+.35*Math.sin(h/1.8*Math.PI);ring(m,v,center.add(0,h,0),X,Z,r,now*.025,0xf5f5ee,fade,quality);}
  for(int i=0;i<6;i++){double a=i*Math.PI/3;stroke(m,v,center.add(Math.cos(a)*.3,0,Math.sin(a)*.3),center.add(Math.cos(a+2)*.3,1.8,Math.sin(a+2)*.3),UP,.016,0xf5f5ee,fade);}
 }
 private static void web(MatrixStack m,VertexConsumer v,Vec3d center,double radius,double fade,int quality){
  int spokes=quality==0?8:16;for(int i=0;i<spokes;i++){double a=i*2*Math.PI/spokes;stroke(m,v,center,center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),UP,.018,0xe9f2ff,fade);}
  for(int i=1;i<=5;i++)ring(m,v,center,X,Z,radius*i/5,0,0xe9f2ff,fade,quality);
 }
 private static void shards(MatrixStack m,VertexConsumer v,Vec3d center,double radius,double now,double fade,int quality){for(int i=0;i<(quality==0?6:12);i++){double a=i*Math.PI/6+now*.008;var p=center.add(Math.cos(a)*radius,1+Math.sin(i*2+now*.03)*.7,Math.sin(a)*radius);polygon(m,v,p,X,UP,.35+(i%3)*.15,3,now*.02+i,0x9fc3e2,fade*.8);}}
 private static void drone(MatrixStack m,VertexConsumer v,Vec3d p,double now,int color,int quality){polygon(m,v,p,X,UP,.3,6,0,0x7890a5,1);polygon(m,v,p.add(0,0,.02),X,UP,.11,8,now*.1,color,1);for(int s:new int[]{-1,1}){stroke(m,v,p,p.add(s*.45,0,0),UP,.065,0xaab8c8,1);ring(m,v,p.add(s*.45,.05,0),X,Z,.2,now*.5,color,.8,0);}}
 private static void bat(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d x,Vec3d y,double size,int color,double fade){
  double[][] points=BAT_POINTS;
  for(int i=0;i<points.length;i++){var a=points[i];var b=points[(i+1)%points.length];quad(m,v,p,p.add(x.multiply(a[0]*size)).add(y.multiply(a[1]*size)),p.add(x.multiply(b[0]*size)).add(y.multiply(b[1]*size)),p,alpha(color,fade));}
 }
 private static void sphere(MatrixStack m,VertexConsumer v,Vec3d p,double radius,int color,double fade,int quality){
  int n=quality==0?8:12,rows=quality==0?5:8;
  for(int j=0;j<rows;j++)for(int i=0;i<n;i++){
   double a=i*Math.PI*2/n,b=(i+1)*Math.PI*2/n,h=-Math.PI/2+j*Math.PI/rows,k=h+Math.PI/rows;
   Vec3d aa=orb(p,radius,a,h),bb=orb(p,radius,b,h),cc=orb(p,radius,b,k),dd=orb(p,radius,a,k);
   quad(m,v,aa,bb,cc,dd,alpha(color,fade*.065));
  }
  for(Vec3d[] plane:new Vec3d[][]{{X,UP},{Z,UP},{X,Z}})ring(m,v,p,plane[0],plane[1],radius,0,color,fade,quality);
 }
 private static Vec3d orb(Vec3d p,double r,double a,double h){return p.add(Math.cos(a)*Math.cos(h)*r,Math.sin(h)*r,Math.sin(a)*Math.cos(h)*r);}
 private static void tether(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d camera,double time,int color,double fade,boolean web){
  Vec3d axis=b.subtract(a),side=axis.crossProduct(UP).normalize();if(side.lengthSquared()<.01)side=X;int n=Math.min(30,Math.max(4,(int)(axis.length()*3)));Vec3d prev=a;
  for(int i=1;i<=n;i++){double t=i/(double)n;var next=a.lerp(b,t).add(side.multiply(Math.sin(t*Math.PI)*Math.sin(t*16+time*.35)*(web?.045:.18)));beam(m,v,prev,next,camera,web?.018:.045,color,fade);if(web)beam(m,v,prev.add(side.multiply(.045)),next.add(side.multiply(.045)),camera,.009,color,fade*.6);prev=next;}
 }
 static void motionLightning(MatrixStack m,WorldRenderContext ctx,Vec3d a,Vec3d b,double time,int color,double width){
  var v=ctx.consumers().getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));lightning(m,v,a,b,ctx.camera().getPos(),time,color,.85,width,0);
 }
 private static void lightning(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d camera,double time,int color,double fade,double width,int quality){
  Vec3d side=b.subtract(a).crossProduct(UP).normalize();if(side.lengthSquared()<.01)side=X;
  int n=Math.clamp((int)a.distanceTo(b)*2,4,26);Vec3d prev=a;
  for(int i=1;i<=n;i++){double t=i/(double)n;Vec3d next=a.lerp(b,t).add(side.multiply(i==n?0:Math.sin(i*13.7+Math.floor(time/2)*3.1)*.32)).add(0,i==n?0:Math.cos(i*4.7+Math.floor(time/2))*.12,0);beam(m,v,prev,next,camera,width,color,fade);
   if(quality>0&&i%5==0)beam(m,v,next,next.add(side.multiply(Math.sin(i)*.8)).add(0,-.8,0),camera,width*.45,color,fade*.8);prev=next;}
 }
 private static void beam(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d camera,double width,int color,double fade){
  if(a.squaredDistanceTo(b)<.00001)return;Vec3d side=b.subtract(a).crossProduct(camera.subtract(a.lerp(b,.5))).normalize();if(side.lengthSquared()<.01)side=X;
  ribbon(m,v,a,b,side.multiply(width*3),alpha(color,.13*fade));ribbon(m,v,a,b,side.multiply(width*1.5),alpha(color,.6*fade));ribbon(m,v,a,b,side.multiply(width*.4),alpha(0xffffff,.98*fade));
 }
 private static void mandala(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d x,Vec3d y,double scale,double angle,int color,double fade,int quality){
  annulus(m,v,p,x,y,scale*.7,scale,angle,color,fade*.09,quality);
  for(double r:new double[]{.27,.57,.71,.86,1})ring(m,v,p,x,y,r*scale,angle,color,fade,quality);
  polygon(m,v,p,x,y,.7*scale,4,angle,color,fade);polygon(m,v,p,x,y,.7*scale,4,angle+Math.PI/4,color,fade);polygon(m,v,p,x,y,.45*scale,3,-angle,color,fade);
  int n=quality==0?12:24;Vec3d normal=x.crossProduct(y);for(int i=0;i<n;i++){double a=i*2*Math.PI/n-angle;var radial=x.multiply(Math.cos(a)).add(y.multiply(Math.sin(a)));var tangent=x.multiply(-Math.sin(a)).add(y.multiply(Math.cos(a)));var q=p.add(radial.multiply(scale*.92));stroke(m,v,q,q.add(radial.multiply(-scale*.1)),normal,.014,color,fade);if(i%2==0)stroke(m,v,q,q.add(tangent.multiply(scale*.055)),normal,.012,color,fade);}
 }
 private static void ring(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d x,Vec3d y,double r,double angle,int color,double fade,int quality){polygon(m,v,p,x,y,r,quality==0?20:40,angle,color,fade);}
 private static void polygon(MatrixStack m,VertexConsumer v,Vec3d p,Vec3d x,Vec3d y,double r,int n,double angle,int color,double fade){var normal=x.crossProduct(y).normalize();for(int i=0;i<n;i++){double a=i*2*Math.PI/n+angle,b=(i+1)*2*Math.PI/n+angle;var from=p.add(x.multiply(Math.cos(a)*r)).add(y.multiply(Math.sin(a)*r));var to=p.add(x.multiply(Math.cos(b)*r)).add(y.multiply(Math.sin(b)*r));stroke(m,v,from,to,normal,.012,color,fade);}}
 private static void stroke(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d normal,double width,int color,double fade){var side=b.subtract(a).crossProduct(normal).normalize();ribbon(m,v,a,b,side.multiply(width*2.8),alpha(color,fade*.17));ribbon(m,v,a,b,side.multiply(width),alpha(color,fade));}
 private static int alpha(int color,double fade){return ((int)(Math.clamp(fade,0,1)*255)<<24)|(color&0xffffff);}
 private static void ribbon(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d s,int color){quad(m,v,a.add(s),a.subtract(s),b.subtract(s),b.add(s),color);}
 private static void quad(MatrixStack m,VertexConsumer v,Vec3d a,Vec3d b,Vec3d c,Vec3d d,int color){if(frameVertices+4>vertexLimit)return;frameVertices+=4;Vec3d[] p={a,b,c,d};for(int i=0;i<4;i++)v.vertex(m.peek(),(float)p[i].x,(float)p[i].y,(float)p[i].z).color(color).texture(i<2?0:1,i==0||i==3?0:1).overlay(OverlayTexture.DEFAULT_UV).light(0xf000f0).normal(m.peek(),0,1,0);}
}
