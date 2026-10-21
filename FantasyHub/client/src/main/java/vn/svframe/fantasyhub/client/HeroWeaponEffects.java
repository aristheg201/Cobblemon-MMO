package vn.svframe.fantasyhub.client;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
/** Server-confirmed weapon and rope segments. Their endpoints are never client target guesses. */
final class HeroWeaponEffects {
 static void render(WorldRenderContext context,Iterable<HeroVisuals.Cue> cues,long tick){
  if(HeroVisuals.density()<=0||context.matrixStack()==null||context.consumers()==null)return;
  var m=context.matrixStack();var camera=context.camera().getPos();float dt=context.tickCounter().getTickDelta(false);
  m.push();m.translate(-camera.x,-camera.y,-camera.z);
  for(var cue:cues){if(!cue.phase().equals("AXE")&&!cue.phase().equals("TETHER")||cue.to().squaredDistanceTo(camera)>4096)continue;
   double t=Math.clamp((tick+dt-cue.start())/Math.max(1,cue.expires()-cue.start()),0,1);Vec3d at=cue.from().lerp(cue.to(),t);
   if(cue.phase().equals("AXE")){m.push();m.translate(at.x,at.y,at.z);HeroEquipmentModels.flyingAxe(m,context.consumers(),tick+dt);m.pop();}
   else{
    var consumer=context.consumers().getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.ofVanilla("textures/misc/white.png")));
    Vec3d a=cue.from(),b=cue.to();var world=net.minecraft.client.MinecraftClient.getInstance().world;var actor=world==null?null:world.getEntityById(cue.entity());if(actor!=null)a=actor.getLerpedPos(dt).add(0,1.35,0);
    Vec3d side=b.subtract(a).crossProduct(camera.subtract(a)).normalize().multiply(.025);var entry=m.peek();
    vertex(consumer,entry,a.add(side));vertex(consumer,entry,a.subtract(side));vertex(consumer,entry,b.subtract(side));vertex(consumer,entry,b.add(side));
   }
  }m.pop();
 }
 private static void vertex(VertexConsumer c,MatrixStack.Entry e,Vec3d p){c.vertex(e.getPositionMatrix(),(float)p.x,(float)p.y,(float)p.z).color(0xffabb8ce).texture(0,0).overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(e,0,1,0);}
}
