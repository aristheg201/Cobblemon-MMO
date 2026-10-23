package vn.svframe.fantasyhub.client;

import java.util.*;
import net.minecraft.client.model.*;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Native animated biped adaptation of the verified Fisk suit atlases, slot by slot. */
public final class HeroArmorFeature extends FeatureRenderer<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> {
  static long qaRenders,qaEmfRenders;
  private static final Set<String> HEROES=Set.of("spider_man","sorcerer_supreme","iron_man","thor","flash","batman");
  private static final Set<String> SEALED_SUITS=Set.of("spider_man","iron_man","flash","batman");
  private static final PlayerEntityModel<LivingEntity> SUIT=new PlayerEntityModel<>(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(new Dilation(.045f),false),64,64).createModel(),false);
  private static final ModelPart[] CAPE=new ModelPart[6];
  static {for(int i=0;i<6;i++)CAPE[i]=new ModelPart(List.of(new ModelPart.Cuboid(0,i*4,-5,0,-.5f,10,4,1,0,0,0,false,64,32,EnumSet.allOf(net.minecraft.util.math.Direction.class))),Map.of());}
  HeroArmorFeature(FeatureRendererContext<AbstractClientPlayerEntity,PlayerEntityModel<AbstractClientPlayerEntity>> context){super(context);}
  public static String suit(ItemStack stack) {
    var nbt=stack.get(DataComponentTypes.CUSTOM_DATA);if(nbt==null)return "";String name=nbt.copyNbt().getString("SVFRAME_ARMOR_TEXTURE"),prefix="fantasyhub:heroes/";
    String hero=name.startsWith(prefix)?name.substring(prefix.length()):"";return HEROES.contains(hero)?hero:"";
  }
  @Override public void render(MatrixStack matrices,VertexConsumerProvider vertices,int light,AbstractClientPlayerEntity entity,float limb,float distance,float delta,float age,float yaw,float pitch) {
    if(entity.isInvisible())return;qaRenders++;
    for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))renderSlot(matrices,vertices,light,entity,slot,getContextModel(),age);
    String chest=suit(entity.getEquippedStack(EquipmentSlot.CHEST));
    var back=vn.svframe.fantasyhub.protocol.BackEquipment.equippedAny(entity);
    String cape=HeroCapePresentation.select(entity.getSkinTextures().capeTexture()!=null&&entity.isPartVisible(net.minecraft.entity.player.PlayerModelPart.CAPE),back.isOf(vn.svframe.fantasyhub.protocol.Protocol.batmanCape),back.isOf(vn.svframe.fantasyhub.protocol.Protocol.thorCape),back.isOf(vn.svframe.fantasyhub.protocol.Protocol.levitationCloak),chest);
    if((back.isOf(vn.svframe.fantasyhub.protocol.Protocol.batmanCape)||entity.isPartVisible(net.minecraft.entity.player.PlayerModelPart.CAPE))&&!cape.isEmpty())cape(matrices,vertices,light,getContextModel(),entity,cape,age);
    HeroEquipmentModels.held(matrices,vertices,light,getContextModel(),entity,age);
  }
  private static void copy(BipedEntityModel<?> source){
    SUIT.child=source.child;SUIT.riding=source.riding;SUIT.handSwingProgress=source.handSwingProgress;SUIT.sneaking=source.sneaking;SUIT.leftArmPose=source.leftArmPose;SUIT.rightArmPose=source.rightArmPose;
    SUIT.head.copyTransform(source.head);SUIT.hat.copyTransform(source.head);SUIT.body.copyTransform(source.body);SUIT.jacket.copyTransform(source.body);
    SUIT.rightArm.copyTransform(source.rightArm);SUIT.rightSleeve.copyTransform(source.rightArm);SUIT.leftArm.copyTransform(source.leftArm);SUIT.leftSleeve.copyTransform(source.leftArm);
    SUIT.rightLeg.copyTransform(source.rightLeg);SUIT.rightPants.copyTransform(source.rightLeg);SUIT.leftLeg.copyTransform(source.leftLeg);SUIT.leftPants.copyTransform(source.leftLeg);
  }
  public static boolean renderSlot(MatrixStack matrices,VertexConsumerProvider vertices,int light,LivingEntity entity,EquipmentSlot slot,BipedEntityModel<?> source,float age) {
    String hero=suit(entity.getEquippedStack(slot));if(hero.isEmpty())return false;
    if(source instanceof PlayerEntityModel<?> playerModel&&source.rightArm.getClass().getName().startsWith("traben.entity_model_features.")){
      renderAnimatedSlot(matrices,vertices,light,slot,playerModel,hero);if(hero.equals("batman"))BatmanArmorDetails.render(matrices,vertices,light,source,slot);qaEmfRenders++;return true;
    }
    copy(source);SUIT.setVisible(false);
    switch(slot) {
      case HEAD -> SUIT.head.visible=SUIT.hat.visible=true;
      case CHEST -> {SUIT.body.visible=SUIT.rightArm.visible=SUIT.leftArm.visible=SUIT.jacket.visible=SUIT.rightSleeve.visible=SUIT.leftSleeve.visible=true;}
      case LEGS,FEET -> SUIT.rightLeg.visible=SUIT.leftLeg.visible=SUIT.rightPants.visible=SUIT.leftPants.visible=true;
      default -> {return false;}
    }
    String part=switch(slot){case HEAD->"head";case CHEST->"chest";case LEGS->"legs";default->"feet";};
    // Render all suit pixels with their original colors, independent of leather dye.
    SUIT.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_"+part+".png"))),light,OverlayTexture.DEFAULT_UV,0xffffffff);
    if(hero.equals("batman")&&slot==EquipmentSlot.HEAD)SUIT.render(matrices,vertices.getBuffer(RenderLayer.getEyes(Identifier.of("fantasyhub","textures/armor/heroes/batman_lights.png"))),0xf000f0,OverlayTexture.DEFAULT_UV,0xffffffff);
    if(hero.equals("iron_man"))SUIT.render(matrices,vertices.getBuffer(RenderLayer.getEyes(Identifier.of("fantasyhub","textures/armor/heroes/iron_man_lights_"+(slot==EquipmentSlot.LEGS?"layer2":"layer1")+".png"))),0xf000f0,OverlayTexture.DEFAULT_UV,0xffffffff);
    if(hero.equals("batman"))BatmanArmorDetails.render(matrices,vertices,light,source,slot);
    return true;
  }
  private static void renderAnimatedSlot(MatrixStack m,VertexConsumerProvider vertices,int light,EquipmentSlot slot,PlayerEntityModel<?> model,String hero){
    List<ModelPart> parts=switch(slot){
      case HEAD->List.of(model.head,model.hat);
      case CHEST->List.of(model.body,model.jacket,model.rightArm,model.rightSleeve,model.leftArm,model.leftSleeve);
      case LEGS,FEET->List.of(model.rightLeg,model.rightPants,model.leftLeg,model.leftPants);
      default->List.of();
    };
    String part=switch(slot){case HEAD->"head";case CHEST->"chest";case LEGS->"legs";default->"feet";};
    m.push();m.scale(1.006f,1.006f,1.006f);
    var normal=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_"+part+".png")));
    for(var bone:parts){boolean visible=bone.visible;bone.visible=true;bone.render(m,normal,light,OverlayTexture.DEFAULT_UV,0xffffffff);bone.visible=visible;}
    if(hero.equals("iron_man")||hero.equals("batman")&&slot==EquipmentSlot.HEAD){
      String path=hero.equals("batman")?"batman_lights":"iron_man_lights_"+(slot==EquipmentSlot.LEGS?"layer2":"layer1");
      var glow=vertices.getBuffer(RenderLayer.getEyes(Identifier.of("fantasyhub","textures/armor/heroes/"+path+".png")));
      for(var bone:parts){boolean visible=bone.visible;bone.visible=true;bone.render(m,glow,0xf000f0,OverlayTexture.DEFAULT_UV,0xffffffff);bone.visible=visible;}
    }
    m.pop();
  }
  private static void cape(MatrixStack matrices,VertexConsumerProvider vertices,int light,PlayerEntityModel<?> source,LivingEntity player,String hero,float age) {
    if(hero.equals("batman")&&player.isFallFlying()){batGlider(matrices,vertices,light,source,age);return;}
    var consumer=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_cape.png")));
    boolean glide=!player.isOnGround()&&player.getVelocity().y<0;double speed=player.getVelocity().horizontalLength();
    matrices.push();source.body.rotate(matrices);matrices.translate(0,0,.155);
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)(8+Math.min(45,speed*70)+(glide?30:0))));
    for(int i=0;i<6;i++){matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)(2+Math.sin(age*.12-i*.6)*1.6)));CAPE[i].render(matrices,consumer,light,OverlayTexture.DEFAULT_UV,0xffffffff);matrices.translate(0,.25,0);}
    matrices.pop();
  }
  static void echoCape(MatrixStack matrices,VertexConsumerProvider vertices,PlayerEntityModel<?> source,AbstractClientPlayerEntity player,float age,int color){
    String chest=suit(player.getEquippedStack(EquipmentSlot.CHEST));var back=vn.svframe.fantasyhub.protocol.BackEquipment.equippedAny(player);
    String hero=HeroCapePresentation.select(player.getSkinTextures().capeTexture()!=null&&player.isPartVisible(net.minecraft.entity.player.PlayerModelPart.CAPE),back.isOf(vn.svframe.fantasyhub.protocol.Protocol.batmanCape),back.isOf(vn.svframe.fantasyhub.protocol.Protocol.thorCape),back.isOf(vn.svframe.fantasyhub.protocol.Protocol.levitationCloak),chest);
    if(hero.isEmpty())return;
    var v=vertices.getBuffer(RenderLayer.getEntityTranslucent(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_cape.png")));
    matrices.push();source.body.rotate(matrices);matrices.translate(0,0,.155);
    double speed=player.getVelocity().horizontalLength();boolean glide=!player.isOnGround()&&player.getVelocity().y<0;
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)(8+Math.min(45,speed*70)+(glide?30:0))));
    for(int i=0;i<6;i++){matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)(2+Math.sin(age*.12-i*.6)*1.6)));CAPE[i].render(matrices,v,0xf000f0,OverlayTexture.DEFAULT_UV,color);matrices.translate(0,.25,0);}
    matrices.pop();
  }
  private static void batGlider(MatrixStack m,VertexConsumerProvider vertices,int light,PlayerEntityModel<?> body,float age){
    var v=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/batman_glider.png")));
    m.push();body.body.rotate(m);m.translate(0,0,.18);
    // Paired membranes fan out from the shoulders; scalloped trailing edge follows airflow.
    for(int sign:new int[]{-1,1})for(int i=0;i<20;i++){
      double t=i/20d,u=(i+1)/20d;
      double[] a=batEdge(t,sign,age),b=batEdge(u,sign,age);
      double[][] q={{0,0,.02},a,b,{0,0,.02}};
      for(int j=0;j<4;j++){double f=j==1?t:j==2?u:0;v.vertex(m.peek(),(float)q[j][0],(float)q[j][1],(float)q[j][2]).color(0xffffffff).texture((float)(.17+sign*(j==0||j==3?0:.15)),(float)(.03+f*.72)).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(m.peek(),0,0,1);}
    }
    m.pop();
  }
  private static double[] batEdge(double t,int sign,float age){
    double x=Math.sin(t*Math.PI)*1.95;
    double y=t<.5?t*.8:.4+(t-.5)*2.2+.14*Math.pow(Math.sin((t-.5)*Math.PI*8),2);
    return new double[]{sign*x,y,.03+Math.sin(age*.1+t*3)*.045+t*.08};
  }
  public static void visibility(PlayerEntityModel<?> model,LivingEntity entity) {
    if(!suit(entity.getEquippedStack(EquipmentSlot.HEAD)).isEmpty())model.head.visible=model.hat.visible=false;
    if(!suit(entity.getEquippedStack(EquipmentSlot.CHEST)).isEmpty())model.jacket.visible=model.rightSleeve.visible=model.leftSleeve.visible=false;
    if(!suit(entity.getEquippedStack(EquipmentSlot.LEGS)).isEmpty())model.leftPants.visible=model.rightPants.visible=false;
    // A sealed suit replaces the underlying skin. Scaling an EMF arm about the
    // model origin moves its inner face inside the skin, so depth alone cannot cover it.
    // The feature temporarily enables these animated bones while drawing the armor.
    if(SEALED_SUITS.contains(suit(entity.getEquippedStack(EquipmentSlot.CHEST))))model.body.visible=model.leftArm.visible=model.rightArm.visible=false;
    String legs=suit(entity.getEquippedStack(EquipmentSlot.LEGS));
    if(SEALED_SUITS.contains(legs)&&legs.equals(suit(entity.getEquippedStack(EquipmentSlot.FEET))))model.leftLeg.visible=model.rightLeg.visible=false;
  }
  public static void firstPerson(AbstractClientPlayerEntity entity,PlayerEntityModel<?> source,MatrixStack matrices,VertexConsumerProvider vertices,int light,boolean left) {
    String hero=suit(entity.getEquippedStack(EquipmentSlot.CHEST));if(hero.isEmpty())return;
    copy(source);SUIT.setVisible(false);var arm=left?SUIT.leftArm:SUIT.rightArm;var sleeve=left?SUIT.leftSleeve:SUIT.rightSleeve;arm.visible=sleeve.visible=true;
    SUIT.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_chest.png"))),light,OverlayTexture.DEFAULT_UV,0xffffffff);
  }
}
