package vn.svframe.fantasyhub.client;

import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class AbilityHud {
  static int lastRenderedSlots;
  private static final Set<String> ICONS=Set.of("death_sentence","death_strike","phantom_charge","cursed_seal","necrotic_whirlwind","wraithbound_chains");
  private AbilityHud() {}
  static Identifier icon(String id) {
    var hero=HeroSkillIcons.icon(id);if(hero!=null)return hero;
    var rider=RiderSkillIcons.icon(id,false);if(rider!=null)return rider;
    String prefix="CLS_DEATH_KNIGHT_";
    if(!id.startsWith(prefix))return null;
    String suffix=id.substring(prefix.length()).toLowerCase(Locale.ROOT);
    return ICONS.contains(suffix)?Identifier.of("death_knight","textures/death_knight_texture/icon_"+suffix+".png"):null;
  }
  static SkillBarPresentation.Layout layout(DrawContext context,int count) {
    int top=context.getScaledWindowHeight()-49;
    if(CompactHud.visible("stamina"))top=context.getScaledWindowHeight()-39-CompactHud.staminaOffset();
    return SkillBarPresentation.layout(context.getScaledWindowWidth(),context.getScaledWindowHeight(),count,top);
  }
  static void transform(DrawContext c,SkillBarPresentation.Layout layout) {
    float scale=SkillBarPresentation.renderScale(MinecraftClient.getInstance().getWindow().getScaleFactor(),FantasyHubClient.hudScale);
    float x=c.getScaledWindowWidth()/2f,y=layout.y()+layout.slotHeight();
    c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);c.getMatrices().translate(-x,-y,0);
  }
  static void small(DrawContext c,String text,float x,float y,float scale,int color) {
    c.getMatrices().push();c.getMatrices().translate(x,y,0);c.getMatrices().scale(scale,scale,1);
    c.drawText(MinecraftClient.getInstance().textRenderer,text,0,0,color,true);c.getMatrices().pop();
  }
  static void render(DrawContext c) {
    var slots=SkillBarPresentation.slots(FantasyHubClient.state());int count=slots.size();if(count==0)return;
    var client=MinecraftClient.getInstance();var l=layout(c,count);int y=l.y();
    transform(c,l);
    try {
    c.fill(l.x()-4,y-3,l.x()+l.width()+4,y+l.slotHeight()+3,0xd909101b);
    var form=RiderVisuals.state(client.player.getId());
    boolean elevated=form!=null&&form.force()==1;
    for(int index=0;index<count;index++) {
      JsonObject skill=slots.get(index).getAsJsonObject();int slot=skill.get("inputSlot").getAsInt();
      int x=l.x()+index*(l.slotWidth()+l.gap()),w=l.slotWidth(),h=l.slotHeight();
      String id=skill.get("id").getAsString();int accent=RiderSkillIcons.color(id);
      boolean stance=elevated&&(id.endsWith("_SURVIVE")||id.equals("DECADE_COMPLETE"));
      c.fill(x,y,x+w,y+h,stance?accent:0xff354459);
      c.fill(x+1,y+1,x+w-1,y+h-1,0xff101a29);
      c.fill(x+1,y+1,x+w-1,y+2,accent);
      if(!id.isEmpty()) {
        Identifier texture=RiderSkillIcons.icon(id,elevated);
        if(texture!=null)c.drawTexture(texture,x+2,y+2,18,18,0,0,32,32,32,32);
        else {
          texture=icon(id);
          if(texture!=null){int size=HeroSkillIcons.icon(id)!=null?32:64;c.drawTexture(texture,x+2,y+2,18,18,0,0,size,size,size,size);}
          else if(skill.has("iconMaterial")) {
            var material=Identifier.tryParse(skill.get("iconMaterial").getAsString());
            if(material!=null) {
              var stack=new ItemStack(Registries.ITEM.get(material));
              if(skill.has("iconCustomModelData")&&!skill.get("iconCustomModelData").isJsonNull())stack.set(DataComponentTypes.CUSTOM_MODEL_DATA,new CustomModelDataComponent(skill.get("iconCustomModelData").getAsInt()));
              c.drawItem(stack,x+3,y+2);
            }
          } else c.drawCenteredTextWithShadow(client.textRenderer,client.textRenderer.trimToWidth(FantasyHubClient.name(skill.get("name").getAsString()).getString(),18),x+11,y+7,0xffdce6f2);
        }
        double remaining=SkillBarPresentation.remaining(skill);
        boolean recast=skill.has("recastAvailable")&&skill.get("recastAvailable").getAsBoolean();
        var params=skill.getAsJsonObject("parameters");
        boolean ready=params==null||(!params.has("mana")||FantasyHubClient.number("mana")>=params.get("mana").getAsDouble())&&(!params.has("stamina")||FantasyHubClient.number("stamina")>=params.get("stamina").getAsDouble());
        if(recast) {
          c.fill(x,y,x+w,y+1,0xff77f2e2);c.fill(x+w-1,y,x+w,y+h,0xff77f2e2);
          small(c,"↻",x+w-7,y+2,.7f,0xffb3fff3);
        } else if(remaining>0) {
          int cover=(int)Math.ceil(18*SkillBarPresentation.cooldownFraction(skill));
          c.fill(x+2,y+20-cover,x+20,y+20,0xbb070c14);
          String label=SkillBarPresentation.cooldownLabel(remaining);
          c.drawCenteredTextWithShadow(client.textRenderer,label,x+11,y+7,0xfff5f8ff);
        } else if(!ready)c.fill(x+2,y+2,x+20,y+20,0x99552a28);
        c.fill(x+1,y+21,x+w-1,y+h-1,0xff080f1a);
        c.fill(x+w-5,y+23,x+w-3,y+25,recast?0xff77f2e2:remaining>0?0xff59677a:ready?0xff8ee0ba:0xfff08068);
        String key=slot>=1&&slot<=client.options.hotbarKeys.length?client.options.hotbarKeys[slot-1].getBoundKeyLocalizedText().getString():String.valueOf(slot);
        key=client.textRenderer.trimToWidth(key,24);
        small(c,key,x+3,y+21,.65f,0xffc3d1e3);
      }
    }
    lastRenderedSlots=count;
    } finally {c.getMatrices().pop();}
  }
}
