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
    int count=SkillBarPresentation.slots(FantasyHubClient.state()).size();
    if(count==0)return;
    var layout=AbilityHud.layout(context,count);
    AbilityHud.transform(context,layout);
    try {
    String mode=form.force()==1?(family.equals("decade")?"complete":"survive"):"normal";
    String label=Text.translatable("fantasyhub.rider.hud."+family).getString()+"  /  "+Text.translatable("fantasyhub.rider.hud."+mode).getString();
    int y=layout.y()-12,color=RiderSkillIcons.color(family.toUpperCase(java.util.Locale.ROOT));
    context.fill(layout.x()-4,y-3,layout.x()+layout.width()+4,layout.y()-3,0xc909101b);
    float scale=.7f;
    AbilityHud.small(context,label,layout.x()+2,y,scale,color);
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
    String passiveLabel=passive.getString();
    float passiveScale=.55f;
    AbilityHud.small(context,passiveLabel,layout.x()+layout.width()-2-client.textRenderer.getWidth(passiveLabel)*passiveScale,y+1,passiveScale,0xffb8c7da);
    } finally {context.getMatrices().pop();}
  }
}
