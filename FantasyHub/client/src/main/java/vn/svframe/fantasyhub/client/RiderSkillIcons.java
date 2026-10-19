package vn.svframe.fantasyhub.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import net.minecraft.util.Identifier;

/** Cached authored skill glyphs, independent of the class's equipment icon. */
final class RiderSkillIcons {
  private static final JsonObject ICONS = load();
  private static final Map<String,Integer> COLORS = Map.of(
      "RYUKI",0xffff713e,"KNIGHT",0xff73baff,"DECADE",0xfff446ab,
      "AGITO",0xffefbf57,"ZOLDA",0xff78d48b,"KABUTO",0xffec4b57);
  private static JsonObject load() {
    try(var in=RiderSkillIcons.class.getResourceAsStream("/assets/fantasyhub/rider-skill-icons.json")) {
      if(in==null)throw new IllegalStateException("Missing Rider skill icons");
      return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
    } catch(java.io.IOException e) {throw new IllegalStateException("Cannot read Rider skill icons",e);}
  }
  static Identifier icon(String id, boolean elevated) {
    var row=ICONS.getAsJsonObject(id.toUpperCase(Locale.ROOT));
    if(row==null)return null;
    String field=elevated&&row.has("survive")?"survive":"normal";
    return Identifier.of("fantasyhub","textures/gui/skills/"+row.get(field).getAsString()+".png");
  }
  static int color(String id) {
    int split=id.indexOf('_');return COLORS.getOrDefault(split<0?id:id.substring(0,split),0xff91a5bd);
  }
}
