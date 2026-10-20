package vn.svframe.fantasyhub.client;
import com.google.gson.*;
import java.io.*;
import java.util.Locale;
import net.minecraft.util.Identifier;
final class HeroSkillIcons {
  private static final JsonObject ICONS=load();
  private static JsonObject load(){try(var in=HeroSkillIcons.class.getResourceAsStream("/assets/fantasyhub/hero-skill-icons.json")){if(in==null)throw new IOException("Missing hero icons");return JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
  static Identifier icon(String id){var name=ICONS.get(id.toUpperCase(Locale.ROOT));return name==null?null:Identifier.of("fantasyhub","textures/gui/skills/"+name.getAsString()+".png");}
}
