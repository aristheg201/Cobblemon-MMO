package vn.svframe.fantasyhub.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;

/** Player skin, full head and equipped hero suit at the native player's scale. */
final class HeroPlayerEcho {
 private static final PlayerEntityModel<AbstractClientPlayerEntity> WIDE=model(false),SLIM=model(true);
 private static PlayerEntityModel<AbstractClientPlayerEntity> model(boolean slim){return new PlayerEntityModel<>(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE,slim),64,64).createModel(),slim);}
 static void render(MatrixStack matrices,WorldRenderContext ctx,AbstractClientPlayerEntity player,Vec3d at,float yaw,double opacity,int tint){
  if(opacity<=.01)return;
  var model=player.getSkinTextures().model()==net.minecraft.client.util.SkinTextures.Model.SLIM?SLIM:WIDE;
  float delta=ctx.tickCounter().getTickDelta(false),age=player.age+delta;
  model.child=player.isBaby();model.riding=player.hasVehicle();model.sneaking=player.isInSneakingPose();model.handSwingProgress=player.getHandSwingProgress(delta);
  model.leftArmPose=model.rightArmPose=net.minecraft.client.render.entity.model.BipedEntityModel.ArmPose.EMPTY;
  model.animateModel(player,player.limbAnimator.getPos(delta),player.limbAnimator.getSpeed(delta),delta);
  model.setAngles(player,player.limbAnimator.getPos(delta),player.limbAnimator.getSpeed(delta),age,MathHelper.lerpAngleDegrees(delta,player.prevHeadYaw,player.headYaw)-yaw,player.getPitch(delta));
  // Hero visibility hooks hide the underlying head for helmets. Echoes render it explicitly.
  model.setVisible(true);model.hat.visible=player.isPartVisible(net.minecraft.entity.player.PlayerModelPart.HAT);
  matrices.push();matrices.translate(at.x,at.y,at.z);matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));
  float scale=.9375f*player.getScale();matrices.scale(-scale,-scale,scale);matrices.translate(0,-1.501,0);
  int color=((int)(Math.clamp(opacity,0,1)*255)<<24)|(tint&0xffffff);
  model.render(matrices,ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(player.getSkinTextures().texture())),0xf000f0,OverlayTexture.DEFAULT_UV,color);
  for(var slot:java.util.List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET)){
   String hero=HeroArmorFeature.suit(player.getEquippedStack(slot));if(hero.isEmpty())continue;
   model.setVisible(false);
   String part=switch(slot){case HEAD->{model.head.visible=model.hat.visible=true;yield "head";}case CHEST->{model.body.visible=model.jacket.visible=model.leftArm.visible=model.leftSleeve.visible=model.rightArm.visible=model.rightSleeve.visible=true;yield "chest";}case LEGS->{model.leftLeg.visible=model.leftPants.visible=model.rightLeg.visible=model.rightPants.visible=true;yield "legs";}default->{model.leftLeg.visible=model.leftPants.visible=model.rightLeg.visible=model.rightPants.visible=true;yield "feet";}};
   model.render(matrices,ctx.consumers().getBuffer(RenderLayer.getEntityTranslucent(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_"+part+".png"))),0xf000f0,OverlayTexture.DEFAULT_UV,color);
  }
  HeroArmorFeature.echoCape(matrices,ctx.consumers(),model,player,age,color);
  matrices.pop();
 }
}
