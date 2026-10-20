package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Verified JMCT Tabula geometry, retaining original cube UVs, local pivots and nested transforms. */
public final class HeroEquipmentModels {
  private static final Map<String,Mesh> MODELS=new HashMap<>();
  private static final PlayerEntityModel<LivingEntity> WIDE_HAND=hand(false),SLIM_HAND=hand(true);
  private static PlayerEntityModel<LivingEntity> hand(boolean slim){
    var model=new PlayerEntityModel<LivingEntity>(net.minecraft.client.model.TexturedModelData.of(PlayerEntityModel.getTexturedModelData(net.minecraft.client.model.Dilation.NONE,slim),64,64).createModel(),slim);
    model.child=false;model.rightArm.setTransform(net.minecraft.client.model.ModelTransform.NONE);model.leftArm.setTransform(net.minecraft.client.model.ModelTransform.NONE);
    model.rightSleeve.copyTransform(model.rightArm);model.leftSleeve.copyTransform(model.leftArm);return model;
  }
  private record Mesh(float width,float height,List<Node> nodes){}
  private record Node(float[] position,float[] rotation,float[] scale,ModelPart.Cuboid cube,List<Node> children) {
    void render(MatrixStack m,VertexConsumer v,int light){m.push();m.translate(position[0]/16,position[1]/16,position[2]/16);m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));m.scale(scale[0],scale[1],scale[2]);cube.renderCuboid(m.peek(),v,light,OverlayTexture.DEFAULT_UV,0xffffffff);for(var n:children)n.render(m,v,light);m.pop();}
  }
  private static float[] vector(JsonObject o,String key,float...fallback){if(!o.has(key))return fallback;var a=o.getAsJsonArray(key);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
  private static List<Node> nodes(JsonArray list,float width,float height) {
    var result=new ArrayList<Node>();for(var entry:list){var o=entry.getAsJsonObject();if(o.has("hidden")&&o.get("hidden").getAsBoolean())continue;
      float[] size=vector(o,"dimensions"),offset=vector(o,"offset"),position=vector(o,"position"),rotation=vector(o,"rotation"),scale=vector(o,"scale",1,1,1);var uv=o.getAsJsonArray("txOffset");float inflate=o.has("mcScale")?o.get("mcScale").getAsFloat():0;
      var cube=new ModelPart.Cuboid(uv.get(0).getAsInt(),uv.get(1).getAsInt(),offset[0],offset[1],offset[2],size[0],size[1],size[2],inflate,inflate,inflate,o.has("txMirror")&&o.get("txMirror").getAsBoolean(),width,height,EnumSet.allOf(net.minecraft.util.math.Direction.class));
      result.add(new Node(position,rotation,scale,cube,o.has("children")?nodes(o.getAsJsonArray("children"),width,height):List.of()));
    }return result;
  }
  private static Mesh load(String name){try(var in=HeroEquipmentModels.class.getResourceAsStream("/assets/fantasyhub/geo/heroes/"+name+".json")) {
    if(in==null)throw new IOException("Missing native weapon "+name);var o=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();float w=o.get("textureWidth").getAsFloat(),h=o.get("textureHeight").getAsFloat();return new Mesh(w,h,nodes(o.getAsJsonArray("cubes"),w,h));
  }catch(IOException failure){throw new IllegalStateException(failure);}}
  public static boolean overrides(LivingEntity player) {
    var state=HeroVisuals.state(player.getId());
    return state!=null&&state.hero().equals("THOR")&&!player.getMainHandStack().isOf(vn.svframe.fantasyhub.protocol.Protocol.stormbreaker)&&!player.getOffHandStack().isOf(vn.svframe.fantasyhub.protocol.Protocol.stormbreaker)&&(player.getMainHandStack().isEmpty()||HeroVisuals.cue(player.getId())!=null);
  }
  private static void weapon(String name,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
    var mesh=MODELS.computeIfAbsent(name,HeroEquipmentModels::load);var consumer=vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(Identifier.of("fantasyhub","textures/item/heroes/"+name+".png")));
    for(var n:mesh.nodes)n.render(matrices,consumer,light);
  }
  static void held(MatrixStack matrices,VertexConsumerProvider vertices,int light,PlayerEntityModel<?> body,LivingEntity player,float age) {
    held(matrices,vertices,light,body,player,false);
  }
  static void heldFirstPerson(MatrixStack matrices,VertexConsumerProvider vertices,int light,PlayerEntityModel<?> body,LivingEntity player) {
    held(matrices,vertices,light,body,player,true);
  }
  private static void held(MatrixStack matrices,VertexConsumerProvider vertices,int light,PlayerEntityModel<?> body,LivingEntity player,boolean firstPerson) {
    if(!overrides(player))return;
    matrices.push();
    (player.getMainArm()==net.minecraft.util.Arm.LEFT?body.leftArm:body.rightArm).rotate(matrices);
    // Third-person reference is a relaxed downward/forward carry, not a raised
    // axe beside the shoulder. Preserve the existing first-person presentation.
    if(firstPerson)heldFirstPersonFrame(matrices,player.getMainArm()==net.minecraft.util.Arm.LEFT);
    else heldFrame(matrices,player.getMainArm()==net.minecraft.util.Arm.LEFT);
    weapon("stormbreaker",matrices,vertices,light);matrices.pop();
  }
  static void heldFrame(MatrixStack matrices,boolean left){
    if(left)matrices.translate(2/16d,0,0);
    matrices.translate(-1/16d,9.5/16d,0);matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45));matrices.translate(1/16d,-9.5/16d,0);
  }
  static void heldFirstPersonFrame(MatrixStack matrices,boolean left){
    if(left)matrices.translate(2/16d,0,0);
    matrices.translate(-1/16d,9.5/16d,0);matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));matrices.translate(1/16d,-9.5/16d,0);
  }
  static void item(net.minecraft.client.render.model.json.ModelTransformationMode mode,MatrixStack matrices,VertexConsumerProvider vertices,int light) {
    matrices.push();itemFrame(matrices);
    weapon("stormbreaker",matrices,vertices,light);
    // Vanilla hides the arm when an item is held. Render a real skin/suit arm
    // at this mesh's grip, sharing all item swing/equip transforms. Animator's
    // third-person-model pass already supplies arms, so never add another there.
    if(mode==net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND||mode==net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_LEFT_HAND){
      // Roll only the blade around the haft; keep the forearm's existing grip.
      matrices.translate(-1/16d,9.5/16d,0);matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90));matrices.translate(1/16d,-9.5/16d,0);
      grippingArm(mode==net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_LEFT_HAND,matrices,vertices,light);
    }
    matrices.pop();
  }
  static void itemFrame(MatrixStack matrices){
    matrices.translate(.5,.5,.5);
    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
    matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90));
    matrices.translate(1/16d,-9.5/16d,0);
  }
  private static void grippingArm(boolean left,MatrixStack matrices,VertexConsumerProvider vertices,int light){
    var player=net.minecraft.client.MinecraftClient.getInstance().player;if(player==null||player.isInvisible())return;
    var model=player.getSkinTextures().model()==net.minecraft.client.util.SkinTextures.Model.SLIM?SLIM_HAND:WIDE_HAND;
    var arm=left?model.leftArm:model.rightArm;var sleeve=left?model.leftSleeve:model.rightSleeve;
    matrices.push();matrices.translate(-1/16d,9.5/16d,0);matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));matrices.translate(1/16d,-9.5/16d,0);
    if(left)matrices.translate(-2/16d,0,0);
    arm.render(matrices,vertices.getBuffer(RenderLayer.getEntitySolid(player.getSkinTextures().texture())),light,OverlayTexture.DEFAULT_UV);
    if(player.isPartVisible(left?net.minecraft.entity.player.PlayerModelPart.LEFT_SLEEVE:net.minecraft.entity.player.PlayerModelPart.RIGHT_SLEEVE))sleeve.render(matrices,vertices.getBuffer(RenderLayer.getEntityTranslucent(player.getSkinTextures().texture())),light,OverlayTexture.DEFAULT_UV);
    HeroArmorFeature.firstPerson(player,model,matrices,vertices,light,left);matrices.pop();
  }
}
