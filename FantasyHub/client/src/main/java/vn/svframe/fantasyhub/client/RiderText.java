package vn.svframe.fantasyhub.client;

import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

final class RiderText {
  static Text name(String id, String fallback) {
    String key = "fantasyhub.skill." + id.toLowerCase(Locale.ROOT);
    return I18n.hasTranslation(key) ? Text.translatable(key) : FantasyHubClient.name(fallback);
  }

  static Text description(String id) {
    String key = "fantasyhub.rider.description." + id.toLowerCase(Locale.ROOT);
    if(I18n.hasTranslation("fantasyhub.hero.description."+id.toLowerCase(Locale.ROOT)))key="fantasyhub.hero.description."+id.toLowerCase(Locale.ROOT);
    var player = MinecraftClient.getInstance().player;
    var state = player == null ? null : RiderVisuals.state(player.getId());
    if (state != null && state.force() == 1 && I18n.hasTranslation(key + ".survive"))
      key += ".survive";
    return I18n.hasTranslation(key) ? Text.translatable(key) : Text.empty();
  }
}
