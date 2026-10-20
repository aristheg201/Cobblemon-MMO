package vn.svframe.fantasyhub.client;
import com.google.gson.*;
import java.io.*;
import net.minecraft.text.Text;
final class HeroText {
  private static final JsonObject LOOKUP=load();
  private static JsonObject load(){try(var in=HeroText.class.getResourceAsStream("/assets/fantasyhub/heroes-text-lookup.json")){if(in==null)throw new IOException("Missing hero text lookup");return JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}}
  static Text name(String raw){var key=LOOKUP.get(raw);return key==null?Text.literal(raw):Text.translatable(key.getAsString());}
}
