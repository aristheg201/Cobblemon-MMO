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
 private static final PlayerEntityModel<LivingEntity> MODEL=new PlayerEntityModel<>(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE,false),64,64).createModel(),false);
 private static final String[] SUIT_PARTS={"head","chest","legs","feet"};
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
    if(ghosts<max&&index++%2==0&&opacity>.03){ghost(m,ctx,sample,opacity);ghosts++;frames++;}
    previous=sample.at();
   }
  }
 }
 private static void ghost(MatrixStack m,WorldRenderContext ctx,HeroMotionHistory.Sample s,double opacity){
  float run=(float)Math.sin(s.tick()*1.5);MODEL.setVisible(false);MODEL.head.visible=MODEL.body.visible=MODEL.rightArm.visible=MODEL.leftArm.visible=MODEL.rightLeg.visible=MODEL.leftLeg.visible=true;
  MODEL.head.pitch=-.1f;MODEL.head.yaw=0;MODEL.body.pitch=.25f;MODEL.body.yaw=0;MODEL.rightArm.pitch=-run*1.2f;MODEL.leftArm.pitch=run*1.2f;MODEL.rightArm.roll=-.12f;MODEL.leftArm.roll=.12f;MODEL.rightLeg.pitch=run;MODEL.leftLeg.pitch=-run;
  m.push();m.translate(s.at().x,s.at().y,s.at().z);m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-s.yaw()));m.scale(-1,-1,1);m.translate(0,-1.501,0);
  int color=((int)(opacity*255)<<24)|0xffc35b;
  // The split chest atlas has transparent head/leg UVs. Render each armor region
  // with its own atlas so an echo includes the runner's head and both legs.
  for(String part:SUIT_PARTS){
   MODEL.setVisible(false);
   switch(part){case "head"->MODEL.head.visible=true;case "chest"->MODEL.body.visible=MODEL.rightArm.visible=MODEL.leftArm.visible=true;default->MODEL.rightLeg.visible=MODEL.leftLeg.visible=true;}
   var texture=Identifier.of("fantasyhub","textures/armor/heroes/flash_"+part+".png");
   MODEL.render(m,ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(texture)),0xf000f0,OverlayTexture.DEFAULT_UV,color);
  }
  m.pop();
 }
}
