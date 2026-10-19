package vn.svframe.fantasyhub.client;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RiderSkillIconsTest {
  @Test void everyDeclaredNativeIconHasDistinctVisiblePixelArt() throws Exception {
    try(var in=getClass().getResourceAsStream("/assets/fantasyhub/rider-skill-icons.json")) {
      assertNotNull(in);var manifest=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
      assertEquals(45,manifest.size());var hashes=new HashSet<String>();
      for(var row:manifest.entrySet()) {
        var texture=RiderSkillIcons.icon(row.getKey(),false);assertNotNull(texture,row.getKey());
        String path="/assets/"+texture.getNamespace()+"/"+texture.getPath();
        try(var png=getClass().getResourceAsStream(path)) {
          assertNotNull(png,path);byte[] bytes=png.readAllBytes();assertTrue(hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))),row.getKey()+" duplicates another skill icon");
          var im=ImageIO.read(new java.io.ByteArrayInputStream(bytes));assertEquals(32,im.getWidth());assertEquals(32,im.getHeight());
          int visible=0,clear=0;for(int x=0;x<32;x++)for(int y=0;y<32;y++)if((im.getRGB(x,y)>>>24)>0)visible++;else clear++;
          assertTrue(visible>60&&clear>60,path+" must be a transparent glyph, not an empty or opaque tile");
        }
      }
    }
  }
  @Test void surviveBranchesUseDifferentGlyphsWhileOtherSkillsKeepTheirIcon() {
    for(String id:new String[]{"RYUKI_STRIKE_VENT","RYUKI_SWORD_VENT","RYUKI_FINAL_VENT","KNIGHT_SWORD_VENT","KNIGHT_FINAL_VENT"})assertNotEquals(RiderSkillIcons.icon(id,false),RiderSkillIcons.icon(id,true));
    assertEquals(RiderSkillIcons.icon("DECADE_COMPLETE",false),RiderSkillIcons.icon("DECADE_COMPLETE",true));
  }
  @Test void unrelatedSkillsRetainExistingFallbackInsteadOfBorrowingABelt() {
    assertNull(RiderSkillIcons.icon("CLS_DEATH_KNIGHT_DEATH_STRIKE",false));
    assertNull(RiderSkillIcons.icon("THUNDERBOLT",false));
  }
}
