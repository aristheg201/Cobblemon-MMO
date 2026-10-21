package vn.svframe.fantasyhub.client;

import java.util.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;

/** Original black plated silhouette; follows the already-animated native/EMF bones. */
final class BatmanArmorDetails {
 private record Plate(ModelPart mesh,int color){}
 private static final Identifier WHITE=Identifier.ofVanilla("textures/misc/white.png");
 private static final Plate[] EARS={box(-3.9f,-11.2f,-1.8f,1.4f,4.2f,2.3f,0xff11151a),box(2.5f,-11.2f,-1.8f,1.4f,4.2f,2.3f,0xff11151a)};
 private static final Plate[] RIGHT_ARM={box(-3.4f,-2.4f,-2.6f,4.8f,3.3f,5.2f,0xff1c2229),box(-3.4f,5,-2.6f,4.8f,4.4f,5.2f,0xff14191f),box(-4.5f,5.3f,.5f,1.5f,.5f,3.3f,0xff303841),box(-4.5f,7,.5f,1.5f,.5f,3.3f,0xff303841)};
 private static final Plate[] LEFT_ARM={box(-1.4f,-2.4f,-2.6f,4.8f,3.3f,5.2f,0xff1c2229),box(-1.4f,5,-2.6f,4.8f,4.4f,5.2f,0xff14191f),box(3,5.3f,.5f,1.5f,.5f,3.3f,0xff303841),box(3,7,.5f,1.5f,.5f,3.3f,0xff303841)};
 private static final Plate[] KNEE={box(-2.15f,5,-2.35f,4.3f,2,1,0xff30373f)};
 private static final Plate[] BOOT={box(-2.2f,8,-2.5f,4.4f,4.1f,4.8f,0xff151a20)};
 private static Plate box(float x,float y,float z,float w,float h,float d,int color){return new Plate(new ModelPart(List.of(new ModelPart.Cuboid(0,0,x,y,z,w,h,d,0,0,0,false,16,16,EnumSet.allOf(Direction.class))),Map.of()),color);}
 static void render(MatrixStack m,VertexConsumerProvider vertices,int light,BipedEntityModel<?> model,EquipmentSlot slot){
  var v=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));
  switch(slot){
   case HEAD->parts(m,v,light,model.head,EARS);
   case CHEST->{parts(m,v,light,model.rightArm,RIGHT_ARM);parts(m,v,light,model.leftArm,LEFT_ARM);}
   case LEGS->{parts(m,v,light,model.rightLeg,KNEE);parts(m,v,light,model.leftLeg,KNEE);}
   case FEET->{parts(m,v,light,model.rightLeg,BOOT);parts(m,v,light,model.leftLeg,BOOT);}
   default->{}
  }
 }
 private static void parts(MatrixStack m,VertexConsumer v,int light,ModelPart bone,Plate[] plates){m.push();bone.rotate(m);for(var p:plates)p.mesh.render(m,v,light,OverlayTexture.DEFAULT_UV,p.color);m.pop();}
}
