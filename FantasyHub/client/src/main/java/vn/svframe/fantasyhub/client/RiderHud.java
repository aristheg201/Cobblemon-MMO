package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

final class RiderHud {
  static void render(DrawContext context) {
    var client = MinecraftClient.getInstance();
    if (client.player == null) return;
    var form = RiderVisuals.state(client.player.getId());
    if (form == null || !java.util.Set.of("ryuki", "knight", "decade").contains(form.form()))
      return;
    var status =
        Text.translatable(
            "fantasyhub.rider.status",
            Text.translatable("fantasyhub.rider.class." + form.form()),
            Text.translatable(
                "fantasyhub.rider.form."
                    + (form.force() == 1
                        ? (form.form().equals("decade") ? "complete" : "survive")
                        : "normal")));
    int y = context.getScaledWindowHeight() - 112;
    context.drawCenteredTextWithShadow(
        client.textRenderer,
        status,
        context.getScaledWindowWidth() / 2,
        y,
        form.form().equals("ryuki")
            ? 0xffffa54e
            : form.form().equals("knight") ? 0xffa2c9ff : 0xffff91d5);
    var state = FantasyHubClient.state().getAsJsonObject("riderStatus");
    if (state == null || !state.has("flame")) return;
    Text passive =
        form.form().equals("ryuki")
            ? Text.translatable(
                "fantasyhub.rider.passive.ryuki",
                state.get("flame").getAsInt(),
                state.get("threshold").getAsInt())
            : form.form().equals("decade") && state.get("cardsReady").getAsBoolean()
                ? Text.translatable("fantasyhub.rider.passive.ready")
                : Text.empty();
    context.getMatrices().push();
    context.getMatrices().translate(context.getScaledWindowWidth() / 2f, y - 8, 0);
    context.getMatrices().scale(.7f, .7f, 1);
    context.drawCenteredTextWithShadow(client.textRenderer, passive, 0, 0, 0xffdddddd);
    context.getMatrices().pop();
  }
}
