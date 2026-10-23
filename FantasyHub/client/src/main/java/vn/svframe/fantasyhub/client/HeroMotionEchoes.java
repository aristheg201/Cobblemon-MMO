package vn.svframe.fantasyhub.client;

import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Full native biped echoes at measured movement samples, with limb-attached multistrand lightning. */
final class HeroMotionEchoes {
 private static final Map<Integer,HeroMotionHistory> HISTORY=new LinkedHashMap<>();
 private static long frames;
 static void clear(){HISTORY.clear();frames=0;}
 static long frames(){return frames;}
 static void render(MatrixStack m,WorldRenderContext ctx,Map<Integer,HeroVisuals.State> states,double now,int quality){
  var mc=MinecraftClient.getInstance();if(mc.world==null)return;
  HISTORY.keySet().removeIf(id->!states.containsKey(id)||!states.get(id).hero().equals("FLASH"));
  int ghosts=0,max=quality==0?3:quality==1?8:14;
  for(var row:states.entrySet()){
   if(!row.getValue().hero().equals("FLASH"))continue;
   var e=mc.world.getEntityById(row.getKey());if(e==null||e.squaredDistanceTo(ctx.camera().getPos())>1600)continue;
   if(HISTORY.size()>=16&&!HISTORY.containsKey(e.getId()))continue;
   var history=HISTORY.computeIfAbsent(e.getId(),id->new HeroMotionHistory());var at=e.getLerpedPos(ctx.tickCounter().getTickDelta(false));history.add(at,e.getYaw(),(long)now);
   if(history.speed()<.035)continue;
   Vec3d forward=e.getRotationVec(1).multiply(1,0,1).normalize(),right=new Vec3d(-forward.z,0,forward.x);double stride=Math.sin(now*1.5);
   // Arm and leg arcs stay attached to the moving actor, rather than orbiting its feet.
   for(int side:new int[]{-1,1}){
    var hip=at.add(right.multiply(side*.18)).add(0,.85,0);var foot=at.add(right.multiply(side*.2)).add(forward.multiply(side*stride*.38)).add(0,.1+Math.max(0,side*stride)*.25,0);
    var shoulder=at.add(right.multiply(side*.33)).add(0,1.4,0);var hand=at.add(right.multiply(side*.4)).add(forward.multiply(-side*stride*.45)).add(0,1.05,0);
    HeroEffects.motionLightning(m,ctx,hip,foot,now+side,0xffc629,.045);HeroEffects.motionLightning(m,ctx,shoulder,hand,now-side,0xffb325,.04);
   }
   Vec3d previous=at;
   int index=0;
   for(var sample:history.samples()){
    double opacity=(1-Math.clamp((now-sample.tick())/10,0,1))*.42;
    for(int lane=0;lane<(quality==0?2:4);lane++){
     Vec3d offset=right.multiply((lane%2==0?-1:1)*(.18+lane*.08)).add(0,.25+lane*.4,0);
     HeroEffects.motionLightning(m,ctx,previous.add(offset),sample.at().add(offset),now+lane, lane%2==0?0xffd33d:0xff6029,.02+lane*.005);
    }
    if(ghosts<max&&index++%2==0&&opacity>.03){if(e instanceof net.minecraft.client.network.AbstractClientPlayerEntity player)HeroPlayerEcho.render(m,ctx,player,sample.at(),sample.yaw(),opacity,0xffffff);ghosts++;frames++;}
    previous=sample.at();
   }
  }
 }
}
