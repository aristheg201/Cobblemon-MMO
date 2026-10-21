package vn.svframe.fantasyhub.core.hero;

import java.util.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import vn.svframe.fantasyhub.protocol.Protocol;

/** One server-owned projection of the real held axe. The physical item is never moved or cloned. */
final class HeroAxeFlight {
 private static final Map<UUID,Flight> FLIGHTS=new HashMap<>();
 private static final class Flight {
  final HeroRuntime.Cast cast;final ItemStack source;final int slot;final Vec3d destination;
  final AxeFlightMath.Hits hits=new AxeFlightMath.Hits();Vec3d at;boolean recall;int age,blocked;
  Flight(HeroRuntime.Cast c){cast=c;var p=c.owner.player;source=p.getMainHandStack();slot=p.getInventory().selectedSlot;at=p.getEyePos();destination=HeroWorld.rayEnd(p,at,at.add(p.getRotationVec(1).multiply(AxeFlightMath.distance(c.ability.range()))));}
 }
 static boolean active(UUID id){return FLIGHTS.containsKey(id);}
 static Map<String,Object> inspect(UUID id){var f=FLIGHTS.get(id);return f==null?Map.of("active",false):Map.of("active",true,"recalling",f.recall,"age",f.age,"position",List.of(f.at.x,f.at.y,f.at.z),"destination",List.of(f.destination.x,f.destination.y,f.destination.z));}
 static void cast(HeroRuntime.Cast c){
  var id=c.owner.player.getUuid();var old=FLIGHTS.get(id);if(old!=null){old.recall=true;return;}
  var f=new Flight(c);FLIGHTS.put(id,f);c.owner.flag("axe_out",12);HeroPresentation.publishNow(c.owner);
 }
 static void release(UUID id){var f=FLIGHTS.remove(id);if(f!=null){f.cast.owner.power.unset("axe_out");f.cast.owner.power.recast(f.cast.ability.id(),HeroRuntime.tick);f.cast.owner.recasts.remove(f.cast.ability.id());HeroPresentation.cue(f.cast,"AXE_END",f.at,f.at,.1);HeroPresentation.publishNow(f.cast.owner);}}
 private static boolean clear(net.minecraft.server.network.ServerPlayerEntity p,Vec3d from,LivingEntity target){var eye=target.getEyePos();return p.getServerWorld().raycast(new net.minecraft.world.RaycastContext(from.lerp(eye,.001),eye,net.minecraft.world.RaycastContext.ShapeType.COLLIDER,net.minecraft.world.RaycastContext.FluidHandling.NONE,p)).getType()==net.minecraft.util.hit.HitResult.Type.MISS;}
 static void tick(){for(var f:List.copyOf(FLIGHTS.values())){
  var c=f.cast;var p=c.owner.player;
  if(!c.valid()||p.getInventory().selectedSlot!=f.slot||p.getMainHandStack()!=f.source||!f.source.isOf(Protocol.stormbreaker)){release(p.getUuid());continue;}
  if(++f.age>=160)f.recall=true;
  Vec3d goal=f.recall?p.getEyePos():f.destination;
  Vec3d delta=goal.subtract(f.at);double speed=c.ability.effect("speed");if(speed<=0)speed=1.5;
  var requested=f.at.add(delta.normalize().multiply(Math.min(speed,delta.length())));var next=HeroWorld.rayEnd(p,f.at,requested);
  boolean obstructed=next.squaredDistanceTo(requested)>.001;
  if(f.recall&&obstructed){if(++f.blocked>20){release(p.getUuid());continue;}}else f.blocked=0;
  if(next.squaredDistanceTo(f.at)>.00001){
   for(var target:HeroWorld.line(p,f.at,next,c.ability.radius(),Integer.MAX_VALUE))if(clear(c.owner.player,f.at,target)&&f.hits.accept(target.getUuidAsString(),f.recall))HeroCombat.hit(c,target,c.ability.phases().get(f.recall?1:0));
  }
  if(next.squaredDistanceTo(f.at)>.00001)HeroPresentation.trail(c,f.at,next);
  HeroPresentation.cue(c,"AXE",f.at,next,.12);f.at=next;
  if(obstructed&&!f.recall){f.recall=true;HeroPresentation.cue(c,"IMPACT",next,next,.5);}
  if(f.recall&&f.at.distanceTo(goal)<.35){release(p.getUuid());continue;}
  if(f.age>=220)release(p.getUuid());
 }}
}
