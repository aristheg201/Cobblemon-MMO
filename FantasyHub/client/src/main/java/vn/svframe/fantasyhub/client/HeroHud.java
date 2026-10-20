package vn.svframe.fantasyhub.client;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** One narrow status row, sharing the existing seven-slot skill-bar layout and scale. */
final class HeroHud {
  static void render(DrawContext context) {
    var client=MinecraftClient.getInstance();if(client.player==null)return;var state=HeroVisuals.state(client.player.getId());if(state==null)return;
    int count=SkillBarPresentation.slots(FantasyHubClient.state()).size();if(count==0)return;
    var layout=AbilityHud.layout(context,count);AbilityHud.transform(context,layout);
    try {
      String hero=state.hero().toLowerCase(Locale.ROOT);int y=layout.y()-12;
      context.fill(layout.x()-4,y-3,layout.x()+layout.width()+4,layout.y()-3,0xc909101b);
      String label=Text.translatable("fantasyhub.hero.name."+hero).getString();
      String passive=switch(state.hero()) {
        case "IRON_MAN"->Text.translatable("fantasyhub.hero.hud.heat",state.heat()).getString();
        case "FLASH"->Text.translatable("fantasyhub.hero.hud.speed",state.speed()).getString();
        case "SPIDER_MAN"->Text.translatable("fantasyhub.hero.hud.instinct",state.stacks()).getString();
        case "SORCERER_SUPREME"->Text.translatable("fantasyhub.hero.hud.mystic",state.stacks()).getString();
        case "THOR"->Text.translatable("fantasyhub.hero.hud.static",state.stacks()).getString();
        default->Text.translatable("fantasyhub.hero.hud.combo",state.stacks()).getString();
      };
      if((state.flags()&1)!=0)passive+=" • "+Text.translatable("fantasyhub.hero.hud.flight").getString();
      if((state.flags()&16)!=0)passive+=" • Overdrive";
      if((state.flags()&32)!=0)passive+=" • "+Text.translatable("fantasyhub.hero.hud.awakened").getString();
      if((state.flags()&4)!=0)passive+=" • "+Text.translatable("fantasyhub.hero.hud.phasing").getString();
      AbilityHud.small(context,label,layout.x()+2,y,.64f,RiderSkillIcons.color(state.hero()+"_"));
      float scale=.54f;float x=layout.x()+layout.width()-2-client.textRenderer.getWidth(passive)*scale;
      AbilityHud.small(context,passive,x,y+1,scale,0xffc1d0df);
    }finally{context.getMatrices().pop();}
  }
}
