package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.*;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import vn.svframe.fantasyhub.core.rider.SweptPath;
import vn.svframe.svframelib.entity.RpgEntityAdapters;
import vn.svframe.svframelib.fabric.ReferenceClassSkillRuntime;

/** Bounded targeting and collision helpers used by the native superhero skill families. */
final class HeroWorld {
  private HeroWorld() {}
  static boolean legal(ServerPlayerEntity caster,LivingEntity target) {
    return ReferenceClassSkillRuntime.canTarget(caster,target);
  }
  static Vec3d forward(LivingEntity entity){return entity.getRotationVec(1).multiply(1,0,1).normalize();}
  static boolean visible(ServerPlayerEntity caster,Vec3d origin,LivingEntity target) {
    return caster.getServerWorld().raycast(new RaycastContext(origin.add(0,.15,0),target.getEyePos(),RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,caster)).getType()==HitResult.Type.MISS;
  }
  static List<LivingEntity> near(ServerPlayerEntity caster,Vec3d center,double radius,int cap) {
    radius=Math.max(0,Math.min(20,radius));double r2=radius*radius;
    return caster.getServerWorld().getEntitiesByClass(LivingEntity.class,new Box(center,center).expand(radius),t->legal(caster,t)&&t.squaredDistanceTo(center)<=r2&&visible(caster,center,t)).stream().sorted(Comparator.comparingDouble(t->t.squaredDistanceTo(center))).limit(cap).toList();
  }
  static List<LivingEntity> cone(ServerPlayerEntity caster,Vec3d center,double radius,double degrees,int cap) {
    Vec3d facing=forward(caster);double dot=Math.cos(Math.toRadians(degrees/2));
    return near(caster,center,radius,cap*2).stream().filter(t->t.getPos().subtract(center).multiply(1,0,1).normalize().dotProduct(facing)>=dot).limit(cap).toList();
  }
  static LivingEntity aim(ServerPlayerEntity caster,double range) {
    var from=caster.getEyePos();var end=rayEnd(caster,from,from.add(caster.getRotationVec(1).multiply(Math.min(40,range))));
    return line(caster,from,end,.2,1).stream().findFirst().orElse(null);
  }
  static List<LivingEntity> line(ServerPlayerEntity caster,Vec3d from,Vec3d destination,double width,int cap) {
    Vec3d end=rayEnd(caster,from,destination);var hits=new ArrayList<Map.Entry<LivingEntity,Vec3d>>();
    for(var target:caster.getServerWorld().getEntitiesByClass(LivingEntity.class,new Box(from,end).expand(width),t->legal(caster,t))) {
      var box=target.getBoundingBox().expand(width);var contact=box.contains(from)?Optional.of(from):box.raycast(from,end);
      contact.ifPresent(point->hits.add(Map.entry(target,point)));
    }
    return hits.stream().sorted(Comparator.comparingDouble(e->e.getValue().squaredDistanceTo(from))).limit(cap).map(Map.Entry::getKey).toList();
  }
  static Vec3d rayEnd(ServerPlayerEntity p,Vec3d from,Vec3d to) {
    Vec3d delta=to.subtract(from);double distance=Math.min(40,delta.length());
    if(distance<.0001)return from;
    Vec3d direction=delta.normalize();
    for(double d=0;d<=distance;d+=.5){var at=from.add(direction.multiply(d));if(at.y<p.getWorld().getBottomY()||at.y>p.getWorld().getTopY()||!p.getWorld().getWorldBorder().contains(BlockPos.ofFloored(at))||!p.getServerWorld().isChunkLoaded(MathHelper.floor(at.x)>>4,MathHelper.floor(at.z)>>4)){to=from.add(direction.multiply(Math.max(0,d-.5)));break;}}
    var hit=p.getServerWorld().raycast(new RaycastContext(from,to,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,p));return hit.getType()==HitResult.Type.MISS?to:hit.getPos();
  }
  static Vec3d point(ServerPlayerEntity p,double range) {return rayEnd(p,p.getEyePos(),p.getEyePos().add(p.getRotationVec(1).multiply(range)));}
  static boolean safe(ServerPlayerEntity p,Vec3d point,boolean collision) {
    if(!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z))return false;
    Box box=p.getBoundingBox().offset(point.subtract(p.getPos()));var world=p.getServerWorld();
    if(box.minY<world.getBottomY()||box.maxY>world.getTopY()||!world.getWorldBorder().contains(box))return false;
    for(int x=MathHelper.floor(box.minX)>>4;x<=MathHelper.floor(box.maxX)>>4;x++)
      for(int z=MathHelper.floor(box.minZ)>>4;z<=MathHelper.floor(box.maxZ)>>4;z++)if(!world.isChunkLoaded(x,z))return false;
    return !collision||world.isSpaceEmpty(p,box);
  }
  static Vec3d move(ServerPlayerEntity p,Vec3d destination,double maximum) {
    if(HeroControl.mobilityLocked(p))return p.getPos();
    Vec3d from=p.getPos(),delta=destination.subtract(from);double length=Math.min(40,Math.min(maximum,delta.length()));
    if(length<=.001)return from;Vec3d movement=delta.normalize().multiply(length);
    double fraction=SweptPath.clearFraction(length,t->safe(p,from.add(movement.multiply(t)),true));
    Vec3d to=from.add(movement.multiply(fraction));teleport(p,to);return to;
  }
  static void teleport(ServerPlayerEntity p,Vec3d to) {
    if(!safe(p,to,true))return;
    p.networkHandler.requestTeleport(to.x,to.y,to.z,p.getYaw(),p.getPitch());p.fallDistance=0;
  }
  static Vec3d portalDestination(ServerPlayerEntity p,double range) {
    // Eye ray resolves cover; the destination uses the same eye-to-feet offset.
    // Back off along the ray if the player's full body does not fit. Air is valid.
    Vec3d end=point(p,range).subtract(0,p.getStandingEyeHeight(),0),from=p.getPos();
    Vec3d delta=end.subtract(from);double length=delta.length();if(length<1)return null;
    for(double distance=length;distance>=1;distance-=.25){Vec3d at=from.add(delta.multiply(distance/length));if(safe(p,at,true))return at;}
    return null;
  }
  static Vec3d landing(ServerPlayerEntity p,Vec3d desired) {
    // Search only a small neighborhood of the actual ray hit; never force-load a destination.
    for(int dy=1;dy>=-3;dy--) {
      Vec3d at=desired.add(0,dy+.05,0);
      if(safe(p,at,true)&&!p.getServerWorld().isSpaceEmpty(p,p.getBoundingBox().offset(at.subtract(p.getPos())).offset(0,-.15,0)))return at;
    }
    return null;
  }
  static boolean behind(ServerPlayerEntity p,LivingEntity target,double distance) {
    Vec3d to=target.getPos().subtract(forward(target).multiply(distance));
    if(p.squaredDistanceTo(to)>64||!safe(p,to,true))return false;
    Vec3d end=rayEnd(p,p.getEyePos(),to.add(0,p.getStandingEyeHeight(),0));
    if(end.squaredDistanceTo(to.add(0,p.getStandingEyeHeight(),0))>.05)return false;
    teleport(p,to);return true;
  }
  static void velocity(LivingEntity target,Vec3d motion) {
    if(!Double.isFinite(motion.lengthSquared()))return;
    target.setVelocity(motion);target.velocityModified=true;target.velocityDirty=true;
    if(target instanceof ServerPlayerEntity player)player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
  }
  static void push(ServerPlayerEntity owner,LivingEntity target,Vec3d center,double horizontal,double vertical) {
    if(!RpgEntityAdapters.allows(owner,target,RpgEntityAdapters.Effect.MOVEMENT))return;
    Vec3d direction=target.getPos().subtract(center).multiply(1,0,1).normalize();
    velocity(target,direction.multiply(Math.min(1.2,horizontal/5)).add(0,Math.min(.8,vertical),0));
  }
}
