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
  static long qaRenders;
  private static final Set<String> HEROES=Set.of("spider_man","sorcerer_supreme","iron_man","thor","flash","batman");
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
    if(chest.equals("batman")||chest.equals("sorcerer_supreme"))cape(matrices,vertices,light,getContextModel(),entity,chest,age);
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
    if(hero.equals("iron_man"))SUIT.render(matrices,vertices.getBuffer(RenderLayer.getEyes(Identifier.of("fantasyhub","textures/armor/heroes/iron_man_lights_"+(slot==EquipmentSlot.LEGS?"layer2":"layer1")+".png"))),0xf000f0,OverlayTexture.DEFAULT_UV,0xffffffff);
    return true;
  }
  private static void cape(MatrixStack matrices,VertexConsumerProvider vertices,int light,PlayerEntityModel<?> source,LivingEntity player,String hero,float age) {
    var consumer=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_cape.png")));
    boolean glide=!player.isOnGround()&&player.getVelocity().y<0;double speed=player.getVelocity().horizontalLength();
    matrices.push();source.body.rotate(matrices);matrices.translate(0,0,.155);
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)(8+Math.min(45,speed*70)+(glide?30:0))));
    for(int i=0;i<6;i++){matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float)(2+Math.sin(age*.12-i*.6)*1.6)));CAPE[i].render(matrices,consumer,light,OverlayTexture.DEFAULT_UV,0xffffffff);matrices.translate(0,.25,0);}
    matrices.pop();
  }
  public static void visibility(PlayerEntityModel<?> model,LivingEntity entity) {
    if(!suit(entity.getEquippedStack(EquipmentSlot.HEAD)).isEmpty())model.head.visible=model.hat.visible=false;
    if(!suit(entity.getEquippedStack(EquipmentSlot.CHEST)).isEmpty())model.jacket.visible=model.rightSleeve.visible=model.leftSleeve.visible=false;
    if(!suit(entity.getEquippedStack(EquipmentSlot.LEGS)).isEmpty())model.leftPants.visible=model.rightPants.visible=false;
  }
  public static void firstPerson(AbstractClientPlayerEntity entity,PlayerEntityModel<?> source,MatrixStack matrices,VertexConsumerProvider vertices,int light,boolean left) {
    String hero=suit(entity.getEquippedStack(EquipmentSlot.CHEST));if(hero.isEmpty())return;
    copy(source);SUIT.setVisible(false);var arm=left?SUIT.leftArm:SUIT.rightArm;var sleeve=left?SUIT.leftSleeve:SUIT.rightSleeve;arm.visible=sleeve.visible=true;
    SUIT.render(matrices,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/"+hero+"_chest.png"))),light,OverlayTexture.DEFAULT_UV,0xffffffff);
  }
}
