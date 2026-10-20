package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.io.InputStreamReader;
import net.minecraft.client.render.model.json.Transformation;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Validate the exported item grip against Minecraft's real third-person hand pipeline. */
class StormbreakerGripTest {
  @Test void realItemGripMatchesBothPlayerFists() throws Exception {
    JsonObject displays;
    try(var in=getClass().getResourceAsStream("/assets/fantasyhub/models/item/stormbreaker.json")) {
      assertNotNull(in);displays=JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject().getAsJsonObject("display");
    }
    for(boolean left:new boolean[]{false,true}) {
      var display=displays.getAsJsonObject(left?"thirdperson_lefthand":"thirdperson_righthand");
      var m=new MatrixStack();
      m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
      m.translate((left?-1:1)/16d,.125,-.625);
      new Transformation(vector(display,"rotation",1),vector(display,"translation",1/16f),vector(display,"scale",1)).apply(left,m);
      m.translate(-.5,-.5,-.5);HeroEquipmentModels.itemFrame(m);
      var grip=new Vector4f(-1/16f,9.5f/16,0,1).mul(m.peek().getPositionMatrix());
      assertEquals((left?1:-1)/16f,grip.x,.0001);assertEquals(9.5f/16,grip.y,.0001);assertEquals(0,grip.z,.0001);
      // Source haft runs along Z and its cutting edge extends along +Y.
      // Reference: relaxed vanilla-style carry. Head below and ahead of the
      // fist, with the cutting edge facing forward in a vertical plane.
      var head=new Vector4f(0,0,-1,0).mul(m.peek().getPositionMatrix());
      var edge=new Vector4f(0,1,0,0).mul(m.peek().getPositionMatrix());
      double diagonal=java.lang.Math.sqrt(.5);
      assertEquals(diagonal,head.y,.0001,"Relaxed carry puts the axe head below the fist");
      assertEquals(-diagonal,head.z,.0001,"Axe head must extend in front of the player");
      assertEquals(-diagonal,edge.z,.0001,"Cutting edge must face forward");
      assertEquals(0,head.x,.0001);assertEquals(0,edge.x,.0001,"Blade plane must remain vertical");
      var virtual=new MatrixStack();HeroEquipmentModels.heldFrame(virtual,left);
      for(var point:new Vector4f[]{new Vector4f(-1/16f,9.5f/16,0,1),new Vector4f(0,0,-1,0),new Vector4f(0,1,0,0)}){
        var actual=new Vector4f(point).mul(m.peek().getPositionMatrix());var fallback=new Vector4f(point).mul(virtual.peek().getPositionMatrix());
        assertEquals(0,actual.distance(fallback),.0001,"Real item and class manifestation must share the same grip and orientation");
      }
    }
  }
  @Test void firstPersonCuttingEdgeFacesAwayFromCamera() throws Exception {
    JsonObject displays;
    try(var in=getClass().getResourceAsStream("/assets/fantasyhub/models/item/stormbreaker.json")){
      displays=JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject().getAsJsonObject("display");
    }
    for(boolean left:new boolean[]{false,true}){
      var display=displays.getAsJsonObject(left?"firstperson_lefthand":"firstperson_righthand");var m=new MatrixStack();
      new Transformation(vector(display,"rotation",1),vector(display,"translation",1/16f),vector(display,"scale",1)).apply(left,m);
      m.translate(-.5,-.5,-.5);HeroEquipmentModels.itemFrame(m);
      var head=new Vector4f(0,0,-1,0).mul(m.peek().getPositionMatrix());var edge=new Vector4f(0,1,0,0).mul(m.peek().getPositionMatrix());
      assertTrue(new Vector3f(head.x,head.y,head.z).normalize().y>.8,"Haft must stand upright");
      assertTrue(new Vector3f(edge.x,edge.y,edge.z).normalize().z<-.8,"Blade must face forward into the scene");
    }
  }
  @Test void firstPersonManifestationRetainsItsExistingPose(){
    for(boolean left:new boolean[]{false,true}){
      var m=new MatrixStack();HeroEquipmentModels.heldFirstPersonFrame(m,left);
      var grip=new Vector4f(-1/16f,9.5f/16,0,1).mul(m.peek().getPositionMatrix());
      assertEquals((left?1:-1)/16f,grip.x,.0001);assertEquals(9.5f/16,grip.y,.0001);assertEquals(0,grip.z,.0001);
      var head=new Vector4f(0,0,-1,0).mul(m.peek().getPositionMatrix());var edge=new Vector4f(0,1,0,0).mul(m.peek().getPositionMatrix());
      assertEquals(-1,head.y,.0001);assertEquals(-1,edge.z,.0001);
    }
  }
  private static Vector3f vector(JsonObject o,String key,float scale){var a=o.getAsJsonArray(key);return new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()).mul(scale);}
}
