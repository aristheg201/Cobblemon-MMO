package vn.svframe.fantasyhub.client;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
/** Original Soulslike Weaponry 1.4.8 model and glowmask, for server-confirmed Mjolnir projectiles. */
final class HeroMjolnir {
 private static HeroBedrockModel.Geometry model;
 private static long renders;
 static long renders(){return renders;}
 static void render(MatrixStack m,VertexConsumerProvider vertices,double spin){
  if(model==null)model=HeroBedrockModel.load("/assets/fantasyhub/native_geometry/heroes/soulslike_mjolnir.geo.json");
  m.push();m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float)spin));m.translate(-.625,0,0);
  for(String suffix:new String[]{"","_glowmask"}){
   var texture=Identifier.of("fantasyhub","textures/item/heroes/soulslike_mjolnir"+suffix+".png");
   var v=vertices.getBuffer(suffix.isEmpty()?RenderLayer.getEntityCutoutNoCull(texture):RenderLayer.getEyes(texture));
   for(var row:model.parents().entrySet())if(row.getValue().isEmpty())model.nodes().get(row.getKey()).render(m,v,0xf000f0);
  }
  m.pop();renders++;
 }
}
