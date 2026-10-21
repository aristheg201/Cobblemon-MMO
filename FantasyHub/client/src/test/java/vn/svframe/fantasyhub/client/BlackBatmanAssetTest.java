package vn.svframe.fantasyhub.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class BlackBatmanAssetTest {
 @Test void originalAssetLedgerMatchesEveryShippedTexture() throws Exception {
  try(var in=getClass().getResourceAsStream("/assets/fantasyhub/black-batman-assets.json")){
   assertNotNull(in);var manifest=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();assertEquals("none; authored UV faces and polygons",manifest.get("sourcePixels").getAsString());assertEquals(11,manifest.getAsJsonArray("files").size());
   for(var item:manifest.getAsJsonArray("files")){var row=item.getAsJsonObject();try(var image=getClass().getResourceAsStream("/assets/fantasyhub/"+row.get("path").getAsString())){assertNotNull(image);assertEquals(row.get("sha256").getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.readAllBytes())));}}
  }
 }
 @Test void suitIsBlackGraphiteWithWhiteEyesAndNoRedBeyondEmblem() throws Exception {
  for(String part:new String[]{"head","chest","legs","feet","cape","glider"})try(var in=getClass().getResourceAsStream("/assets/fantasyhub/textures/armor/heroes/batman_"+part+".png")){
   var image=ImageIO.read(in);int opaque=0,dark=0,red=0;
   for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int p=image.getRGB(x,y);if((p>>>24)<128)continue;opaque++;int r=p>>16&255,g=p>>8&255,b=p&255;if(Math.max(r,Math.max(g,b))<100)dark++;if(r>120&&r>g*1.5&&r>b*1.5)red++;}
   assertTrue(opaque>0,part);assertTrue(dark/(double)opaque>.9,part);assertEquals(0,red,part);
  }
  try(var in=getClass().getResourceAsStream("/assets/fantasyhub/textures/armor/heroes/batman_lights.png")){var image=ImageIO.read(in);assertTrue((image.getRGB(8,10)>>>24)>0);assertEquals(0,image.getRGB(23,22)>>>24);}
 }
 @Test void largerDetailedAnimationSkinLayersHaveOpaqueArmorCoverage() throws Exception {
  try(var in=getClass().getResourceAsStream("/assets/fantasyhub/textures/armor/heroes/batman_chest.png")){var im=ImageIO.read(in);for(int[] uv:new int[][]{{20,36},{44,36},{52,52}})assertEquals(255,im.getRGB(uv[0],uv[1])>>>24,"jacket/sleeve coverage");}
  for(String part:new String[]{"legs","feet"})try(var in=getClass().getResourceAsStream("/assets/fantasyhub/textures/armor/heroes/batman_"+part+".png")){var im=ImageIO.read(in);int y=part.equals("legs")?38:46;assertEquals(255,im.getRGB(4,y)>>>24);assertEquals(255,im.getRGB(4,y+16)>>>24);}
 }

}
