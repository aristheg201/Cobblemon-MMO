package vn.svframe.fantasyhub.core.hero;
import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import vn.svframe.fantasyhub.protocol.*;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframelib.entity.RpgEntityAdapters;

/** Physical rope lease. No teleport or fake swing; native movement handles block collisions. */
final class HeroGrapple {
 private static final Map<UUID,Rope> ROPES=new HashMap<>();
 private static final class Rope {
  final HeroRuntime.Cast cast;final Vec3d anchor;final LivingEntity target;final BlockPos block;double length;int age;
  Rope(HeroRuntime.Cast c,Vec3d anchor,LivingEntity target){cast=c;this.anchor=anchor;this.target=target;length=c.owner.player.getEyePos().distanceTo(anchor)*.92;block=BlockPos.ofFloored(anchor.add(c.owner.player.getRotationVec(1).multiply(.04)));}
 }
 static java.util.Map<String,Object> inspect(UUID id){var r=ROPES.get(id);return r==null?java.util.Map.of("attached",false):java.util.Map.of("attached",true,"length",r.length,"age",r.age,"anchor",java.util.List.of(r.anchor.x,r.anchor.y,r.anchor.z),"target",r.target==null?"block":r.target.getUuidAsString());}
 static void install(){GrapplingHookItem.handler(player->{
  if(!(player instanceof ServerPlayerEntity p))return false;
  if(ROPES.containsKey(p.getUuid())){release(p.getUuid());return true;}
  try{var r=SVFrameMMO.skillRuntime().cast(SVFrameMMO.playerData().get(p),"BATMAN_BATCLAW");return r!=null&&r.isSuccessful();}catch(IllegalArgumentException|IllegalStateException e){return false;}
 });}
 static void cast(HeroRuntime.Cast c){
  var p=c.owner.player;release(p.getUuid());
  Vec3d end=HeroWorld.point(p,28);var target=HeroWorld.aim(p,28);
  if(target!=null&&!RpgEntityAdapters.allows(p,target,RpgEntityAdapters.Effect.MOVEMENT))return;
  var blockHit=p.getServerWorld().raycast(new RaycastContext(p.getEyePos(),p.getEyePos().add(p.getRotationVec(1).multiply(28)),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));
  if(target==null&&blockHit.getType()!=HitResult.Type.BLOCK)return;
  var rope=new Rope(c,target!=null?target.getEyePos():end,target);ROPES.put(p.getUuid(),rope);
  HeroPresentation.cue(c,"TETHER",p.getEyePos(),rope.anchor,.2);
 }
 static void release(UUID id){var r=ROPES.remove(id);if(r!=null)HeroPresentation.cue(r.cast,"TETHER_END",r.cast.owner.player.getEyePos(),r.anchor,.1);}
 static void tick(){for(var r:List.copyOf(ROPES.values())){
  var c=r.cast;var p=c.owner.player;var at=r.target!=null?r.target.getEyePos():r.anchor;int flags=c.owner.inputFlags;
  if(!c.valid()||++r.age>200||!TetherPhysics.attached((flags&1)!=0,(flags&4)!=0,p.isAlive()&&!p.hasVehicle()&&!HeroControl.mobilityLocked(p))||p.getEyePos().squaredDistanceTo(at)>34*34){release(p.getUuid());continue;}
  var clipped=HeroWorld.rayEnd(p,p.getEyePos(),at);if(clipped.distanceTo(at)>.5){release(p.getUuid());continue;}
  if(r.target!=null){
   if(!r.target.isAlive()||!HeroWorld.legal(p,r.target)||!RpgEntityAdapters.allows(p,r.target,RpgEntityAdapters.Effect.MOVEMENT)){release(p.getUuid());continue;}
   Vec3d d=p.getEyePos().subtract(at);if(d.length()<2){
    if(HeroCombat.hit(c,r.target,c.ability.phases().get(0))){HeroActions.debuffs(c,r.target);HeroControl.apply(c.owner,r.target,HeroControl.Kind.STUN,c.ability.effect("stun"));HeroPresentation.cue(c,"IMPACT",r.target.getPos(),r.target.getPos(),.65);}
    release(p.getUuid());continue;
   }
   HeroWorld.velocity(r.target,d.normalize().multiply(Math.min(.65,d.length()*.08)).add(0,.08,0));
  }else{
   if((flags&16)!=0)r.length=TetherPhysics.reel(r.length);
   Vec3d vel=p.getVelocity();var v=TetherPhysics.velocity(p.getX(),p.getY()+p.getStandingEyeHeight(),p.getZ(),at.x,at.y,at.z,r.length,vel.x,vel.y,vel.z,false);
   // Moving forward supplies a small tangential pump while keeping the rope constraint.
   Vec3d motion=new Vec3d(v[0],v[1],v[2]);if((flags&2)!=0){var radial=at.subtract(p.getEyePos()).normalize();var forward=HeroWorld.forward(p);motion=motion.add(forward.subtract(radial.multiply(forward.dotProduct(radial))).multiply(.035));}
   HeroWorld.velocity(p,motion);p.fallDistance=0;
  }
  HeroPresentation.cue(c,"TETHER",p.getEyePos(),at,.15);
 }}
}
