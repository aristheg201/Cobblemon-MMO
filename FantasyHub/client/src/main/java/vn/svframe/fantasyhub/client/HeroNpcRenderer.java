package vn.svframe.fantasyhub.client;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Identifier;
import vn.svframe.fantasyhub.protocol.HeroNpcEntity;
/** Player-shaped class boss with actual animated suit/equipment, not an armor-stand projection. */
final class HeroNpcRenderer extends BipedEntityRenderer<HeroNpcEntity,PlayerEntityModel<HeroNpcEntity>> {
 private static PlayerEntityModel<HeroNpcEntity> model(){return new PlayerEntityModel<>(TexturedModelData.of(PlayerEntityModel.getTexturedModelData(Dilation.NONE,false),64,64).createModel(),false){
  @Override public void setAngles(HeroNpcEntity entity,float limb,float distance,float age,float yaw,float pitch){super.setAngles(entity,limb,distance,age,yaw,pitch);HeroVisuals.animate(this,entity,age);}
 };}
 HeroNpcRenderer(EntityRendererFactory.Context context){super(context,model(),.5f);
  addFeature(new HeldItemFeatureRenderer<>(this,context.getHeldItemRenderer()));
  addFeature(new FeatureRenderer<HeroNpcEntity,PlayerEntityModel<HeroNpcEntity>>(this){
   private final ModelPart cape=new ModelPart(java.util.List.of(new ModelPart.Cuboid(0,0,-5,0,-.5f,10,16,1,0,0,0,false,64,32,java.util.EnumSet.allOf(net.minecraft.util.math.Direction.class))),java.util.Map.of());
   @Override public void render(MatrixStack m,VertexConsumerProvider vertices,int light,HeroNpcEntity e,float limb,float distance,float delta,float age,float yaw,float pitch){
    if(e.isInvisible())return;
    for(var slot:java.util.List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET))HeroArmorFeature.renderSlot(m,vertices,light,e,slot,getContextModel(),age);
    if(java.util.Set.of("BATMAN","THOR","SORCERER_SUPREME").contains(e.hero())){
     m.push();getContextModel().body.rotate(m);m.translate(0,0,.15);m.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(8+(float)Math.sin(age*.1)*4));
     cape.render(m,vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/armor/heroes/"+e.hero().toLowerCase(java.util.Locale.ROOT)+"_cape.png"))),light,OverlayTexture.DEFAULT_UV);m.pop();
    }
   }
  });
 }
 @Override public Identifier getTexture(HeroNpcEntity e){return Identifier.ofVanilla("textures/entity/player/wide/steve.png");}
}
