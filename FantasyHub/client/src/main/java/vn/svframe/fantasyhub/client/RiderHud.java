package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

final class RiderHud {
  static void render(DrawContext context) {
    var client = MinecraftClient.getInstance();
    if (client.player == null) return;
    var form = RiderVisuals.state(client.player.getId());
    String family = form != null && form.form().equals("ryuki_survive") ? "ryuki" : form == null ? "" : form.form();
    if (form == null || !java.util.Set.of("ryuki", "knight", "decade").contains(family))
      return;
    var status =
        Text.translatable(
            "fantasyhub.rider.status",
            Text.translatable("fantasyhub.rider.class." + family),
            Text.translatable(
                "fantasyhub.rider.form."
                    + (form.force() == 1
                        ? (family.equals("decade") ? "complete" : "survive")
                        : "normal")));
    int y = context.getScaledWindowHeight() - 112;
    context.drawCenteredTextWithShadow(
        client.textRenderer,
        status,
        context.getScaledWindowWidth() / 2,
        y,
        family.equals("ryuki")
            ? 0xffffa54e
            : family.equals("knight") ? 0xffa2c9ff : 0xffff91d5);
    var state = FantasyHubClient.state().getAsJsonObject("riderStatus");
    if (state == null || !state.has("flame")) return;
    Text passive =
        family.equals("ryuki")
            ? Text.translatable(
                "fantasyhub.rider.passive.ryuki",
                state.get("flame").getAsInt(),
                state.get("threshold").getAsInt())
            : family.equals("decade") && state.get("cardsReady").getAsBoolean()
                ? Text.translatable("fantasyhub.rider.passive.ready")
                : Text.empty();
    context.getMatrices().push();
    context.getMatrices().translate(context.getScaledWindowWidth() / 2f, y - 8, 0);
    context.getMatrices().scale(.7f, .7f, 1);
    context.drawCenteredTextWithShadow(client.textRenderer, passive, 0, 0, 0xffdddddd);
    context.getMatrices().pop();
  }
}
