package vn.svframe.fantasyhub.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Actual attribute allocations and loadout edits go through the authoritative core API. */
public final class HubScreen extends Screen {
    public enum View { PROFILE, ATTRIBUTES, SKILLS, SETTINGS }
    private final View view;
    private int page,selectedSlot=1;
    private String selection="";
    private boolean requested;
    private int left,top,panelWidth,panelHeight;
    public HubScreen(View view){super(Text.translatable("fantasyhub.screen."+view.name().toLowerCase(java.util.Locale.ROOT)));this.view=view;}
    public void refresh(){clearAndInit();}
    private int attributeRows(){return Math.max(1,(panelHeight-112)/24);}
    @Override protected void init(){
        panelWidth=Math.min(width-20,480);panelHeight=Math.min(height-20,300);left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        int tabWidth=(panelWidth-20)/4;
        for(int i=0;i<View.values().length;i++){
            View target=View.values()[i];
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.screen."+target.name().toLowerCase(java.util.Locale.ROOT)),b->client.setScreen(new HubScreen(target))).dimensions(left+10+i*tabWidth,top+28,tabWidth-2,20).build());
        }
        if(!requested){requested=true;FantasyHubClient.send("refresh","",0);}
        if(view==View.ATTRIBUTES){
            var rows=FantasyHubClient.state().getAsJsonArray("attributes");
            int visible=attributeRows();if(rows!=null)page=Math.min(page,Math.max(0,(rows.size()-1)/visible));
            if(rows!=null)for(int i=0;i<visible&&page*visible+i<rows.size();i++){
                var row=rows.get(page*visible+i).getAsJsonObject();String id=row.get("id").getAsString();
                ButtonWidget b=FantasyButton.builder(Text.translatable("fantasyhub.allocate"),button->FantasyHubClient.send("allocate",id,1)).dimensions(left+panelWidth-80,top+70+i*24,68,20).build();
                b.active=FantasyHubClient.number("attributePoints")>0;addDrawableChild(b);
            }
            ButtonWidget b=FantasyButton.builder(Text.translatable("fantasyhub.respec"),button->FantasyHubClient.send("respec","",0)).dimensions(left+12,top+panelHeight-32,112,20).build();
            b.active=FantasyHubClient.number("respecPoints")>0;addDrawableChild(b);
            addDrawableChild(FantasyButton.builder(Text.literal("‹"),button->{page=Math.max(0,page-1);clearAndInit();}).dimensions(left+130,top+panelHeight-32,22,20).build());
            addDrawableChild(FantasyButton.builder(Text.literal("›"),button->{if(rows!=null&&(page+1)*visible<rows.size())page++;clearAndInit();}).dimensions(left+156,top+panelHeight-32,22,20).build());
        }
        if(view==View.SKILLS){
            var rows=FantasyHubClient.state().getAsJsonArray("skills");int visible=Math.max(1,(panelHeight-138)/22);
            if(rows!=null)for(int i=0;i<visible&&page*visible+i<rows.size();i++){
                var row=rows.get(page*visible+i).getAsJsonObject();String id=row.get("id").getAsString();
                var b=FantasyButton.builder(FantasyHubClient.name(row.get("name").getAsString()),button->{selection=id;clearAndInit();}).dimensions(left+12,top+63+i*22,panelWidth-102,20).build();
                var parameters=row.getAsJsonObject("parameters");
                if(parameters!=null)b.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("fantasyhub.skill.details",row.get("level").getAsInt(),row.has("requiredLevel")?row.get("requiredLevel").getAsInt():1,parameter(parameters,"mana"),parameter(parameters,"stamina"),parameter(parameters,"cooldown"),row.get("cooldownRemaining").getAsDouble())));
                addDrawableChild(b);
                var upgrade=FantasyButton.builder(Text.translatable("fantasyhub.upgrade"),button->FantasyHubClient.send("upgrade",id,1)).dimensions(left+panelWidth-84,top+63+i*22,72,20).build();
                upgrade.active=row.get("owned").getAsBoolean()&&FantasyHubClient.number("skillPoints")>0
                        &&(!row.has("upgradable")||row.get("upgradable").getAsBoolean())
                        &&(!row.has("maxLevel")||row.get("maxLevel").getAsInt()==0||row.get("level").getAsInt()<row.get("maxLevel").getAsInt());addDrawableChild(upgrade);
            }
            int bottom=top+panelHeight-62;
            addDrawableChild(FantasyButton.builder(Text.literal("‹"),b->{page=Math.max(0,page-1);clearAndInit();}).dimensions(left+12,bottom,22,20).build());
            addDrawableChild(FantasyButton.builder(Text.literal("›"),b->{if(rows!=null&&(page+1)*visible<rows.size())page++;clearAndInit();}).dimensions(left+38,bottom,22,20).build());
            int actionWidth=Math.max(48,(panelWidth-90)/3);
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.slot",selectedSlot),b->{var slots=FantasyHubClient.state().getAsJsonArray("loadoutSlots");if(slots!=null&&!slots.isEmpty()){int index=0;for(int i=0;i<slots.size();i++)if(slots.get(i).getAsInt()==selectedSlot)index=i;selectedSlot=slots.get((index+1)%slots.size()).getAsInt();}else selectedSlot=selectedSlot%6+1;clearAndInit();}).dimensions(left+66,bottom,actionWidth,20).build());
            var bind=FantasyButton.builder(Text.translatable("fantasyhub.equip"),b->FantasyHubClient.send("bind",selection,selectedSlot)).dimensions(left+70+actionWidth,bottom,actionWidth,20).build();bind.active=bindable(rows,selection,selectedSlot);addDrawableChild(bind);
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.unequip"),b->FantasyHubClient.send("unbind","",selectedSlot)).dimensions(left+74+actionWidth*2,bottom,actionWidth,20).build());
        }
        if(view==View.SETTINGS){
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.hud.toggle"),b->{FantasyHubClient.hudEnabled=!FantasyHubClient.hudEnabled;FantasyHubClient.saveOptions();}).dimensions(left+12,top+66,160,20).build());
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.hud.scale.down"),b->{FantasyHubClient.hudScale=Math.max(.5f,FantasyHubClient.hudScale-.1f);FantasyHubClient.saveOptions();}).dimensions(left+12,top+94,78,20).build());
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.hud.scale.up"),b->{FantasyHubClient.hudScale=Math.min(2f,FantasyHubClient.hudScale+.1f);FantasyHubClient.saveOptions();}).dimensions(left+96,top+94,78,20).build());
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.hud.position"),b->{FantasyHubClient.options.right=!FantasyHubClient.options.right;FantasyHubClient.saveOptions();}).dimensions(left+12,top+122,160,20).build());
            addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.hud.compact"),b->{FantasyHubClient.options.compact=!FantasyHubClient.options.compact;FantasyHubClient.saveOptions();}).dimensions(left+12,top+150,160,20).build());
            String[] components={"health","mana","stamina","magic"};for(int i=0;i<components.length;i++){
                String component=components[i];addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.hud.component",Text.translatable("fantasyhub.resource."+component)),b->FantasyHubClient.options.toggle(component)).dimensions(left+panelWidth/2+8,top+66+i*28,panelWidth/2-20,20).build());
            }
            addDrawableChild(FantasyButton.builder(Text.translatable("options.controls"),b->client.setScreen(new net.minecraft.client.gui.screen.option.ControlsOptionsScreen(this,client.options))).dimensions(left+12,top+panelHeight-29,panelWidth-112,20).build());
        }
        if(view==View.PROFILE&&FantasyHubClient.state().has("pokemonShop")&&!FantasyHubClient.state().getAsJsonArray("pokemonShop").isEmpty())addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.shop.title"),b->client.setScreen(new PokemonShopScreen())).dimensions(left+12,top+panelHeight-29,panelWidth-112,20).build());
        addDrawableChild(FantasyButton.builder(Text.translatable("gui.done"),b->close()).dimensions(left+panelWidth-84,top+panelHeight-28,72,20).build());
    }
    private static boolean bindable(com.google.gson.JsonArray rows,String id,int slot){if(rows==null)return false;for(var raw:rows){var r=raw.getAsJsonObject();if(r.get("id").getAsString().equals(id)){boolean allowed=!r.has("slots");if(r.has("slots"))for(var value:r.getAsJsonArray("slots"))if(value.getAsInt()==slot)allowed=true;return allowed&&r.get("owned").getAsBoolean()&&r.get("bindable").getAsBoolean();}}return false;}
    private static String parameter(JsonObject parameters,String key){return String.format(java.util.Locale.ROOT,"%.1f",parameters.has(key)?parameters.get(key).getAsDouble():0);}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        renderBackground(context,mouseX,mouseY,delta);FantasyPanel.frame(context,left,top,panelWidth,panelHeight);
        context.drawCenteredTextWithShadow(textRenderer,title,left+panelWidth/2,top+12,0xffe7c37d);
        context.enableScissor(left+8,top+53,left+panelWidth-8,top+panelHeight-28);
        if(view==View.PROFILE){
            context.drawText(textRenderer,FantasyHubClient.name(FantasyHubClient.string("className")),left+16,top+65,0xffe7c37d,false);
            context.drawText(textRenderer,Text.translatable("fantasyhub.hud.level",(int)FantasyHubClient.number("level")),left+16,top+84,0xffeee2c8,false);
            int y=top+110;for(String resource:new String[]{"health","mana","stamina","magic"}){
                FantasyPanel.bar(context,left+16,y,panelWidth-32,15,FantasyHubClient.number(resource),FantasyHubClient.number(resource+"Max"),0xff6e4740,resource);y+=21;
            }
        }else if(view==View.ATTRIBUTES){
            var rows=FantasyHubClient.state().getAsJsonArray("attributes");int visible=attributeRows();if(rows!=null)for(int i=0;i<visible&&page*visible+i<rows.size();i++){
                var r=rows.get(page*visible+i).getAsJsonObject();String label=FantasyHubClient.name(r.get("name").getAsString()).getString()+"  "+r.get("base").getAsInt()+" / "+r.get("total").getAsInt();
                context.drawText(textRenderer,textRenderer.trimToWidth(label,panelWidth-108),left+14,top+76+i*24,0xffeee2c8,false);
            }
            context.drawText(textRenderer,Text.translatable("fantasyhub.points",(int)FantasyHubClient.number("attributePoints")),left+14,top+54,0xffd5ae66,false);
        }else if(view==View.SKILLS){
            var loadout=FantasyHubClient.state().getAsJsonObject("loadout");String equipped=loadout!=null&&loadout.has(String.valueOf(selectedSlot))?loadout.get(String.valueOf(selectedSlot)).getAsString():"";
            Text loadoutText=Text.translatable("fantasyhub.loadout",selectedSlot,equipped.isEmpty()?Text.translatable("fantasyhub.empty"):FantasyHubClient.skillName(equipped));
            context.drawText(textRenderer,textRenderer.trimToWidth(loadoutText.getString(),Math.max(20,panelWidth-110)),left+14,top+panelHeight-38,0xffd5ae66,false);
        }
        context.disableScissor();
        for(var child:children())if(child instanceof net.minecraft.client.gui.Drawable drawable)drawable.render(context,mouseX,mouseY,delta);
    }
    @Override public boolean shouldPause(){return false;}
}
