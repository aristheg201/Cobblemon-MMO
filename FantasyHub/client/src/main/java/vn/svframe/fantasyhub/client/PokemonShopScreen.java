package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.text.Text;
import java.util.*;

/** Separate move shop. All purchase, ownership, equip and cast decisions belong to the server. */
public final class PokemonShopScreen extends Screen {
    private int left,top,w,h,page,slot=1,detailScroll;private String type="",category="",selected="";private boolean ownedOnly,requested;
    public PokemonShopScreen(){super(Text.translatable("fantasyhub.shop.title"));}
    public void refresh(){clearAndInit();}
    private List<JsonObject> rows(){var array=FantasyHubClient.state().getAsJsonArray("pokemonShop");if(array==null)return List.of();var result=new ArrayList<JsonObject>();for(var raw:array){var row=raw.getAsJsonObject();if((type.isEmpty()||type.equals(row.get("type").getAsString()))&&(category.isEmpty()||category.equals(row.get("category").getAsString()))&&(!ownedOnly||row.get("owned").getAsBoolean()))result.add(row);}return result;}
    private JsonObject selection(){for(var row:rows())if(selected.equals(row.get("id").getAsString()))return row;return null;}
    private int visible(){return Math.max(1,(h-130)/23);}
    private void cycle(boolean types){var values=new TreeSet<String>();var array=FantasyHubClient.state().getAsJsonArray("pokemonShop");if(array!=null)for(var raw:array)values.add(raw.getAsJsonObject().get(types?"type":"category").getAsString());var options=new ArrayList<String>();options.add("");options.addAll(values);String old=types?type:category;String next=options.get((options.indexOf(old)+1)%options.size());if(types)type=next;else category=next;page=0;clearAndInit();}
    private static Text name(JsonObject row){String key=row.get("nameKey").getAsString();return key.isEmpty()?FantasyHubClient.name(row.get("name").getAsString()):Text.translatable(key);}
    private static net.minecraft.text.MutableText pokemonType(String type){return Text.translatable("cobblemon.type."+type.toLowerCase(java.util.Locale.ROOT));}
    @Override protected void init(){
        w=Math.min(width-20,480);h=Math.min(height-20,300);left=(width-w)/2;top=(height-h)/2;
        if(!requested){requested=true;FantasyHubClient.send("refresh","",0);}
        int filter=(w-28)/3;
        addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.shop.filter.type",type.isEmpty()?Text.translatable("fantasyhub.shop.all"):pokemonType(type)),b->cycle(true)).dimensions(left+10,top+30,filter,20).build());
        addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.shop.filter.category",category.isEmpty()?Text.translatable("fantasyhub.shop.all"):Text.translatable("fantasyhub.shop.category."+category)),b->cycle(false)).dimensions(left+14+filter,top+30,filter,20).build());
        addDrawableChild(FantasyButton.builder(Text.translatable(ownedOnly?"fantasyhub.shop.owned":"fantasyhub.shop.catalog"),b->{ownedOnly=!ownedOnly;page=0;clearAndInit();}).dimensions(left+18+filter*2,top+30,filter,20).build());
        var rows=rows();int visible=visible();page=Math.min(page,Math.max(0,(rows.size()-1)/visible));int listWidth=Math.max(92,w/2-18);
        for(int i=0;i<visible&&page*visible+i<rows.size();i++){
            var row=rows.get(page*visible+i);String id=row.get("id").getAsString();
            var button=FantasyButton.builder(name(row),b->{selected=id;detailScroll=0;clearAndInit();}).dimensions(left+12,top+60+i*23,listWidth,20).build();
            Text description=row.get("descriptionKey").getAsString().isEmpty()?Text.literal(row.get("description").getAsString()):Text.translatable(row.get("descriptionKey").getAsString());
            button.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.translatable("fantasyhub.shop.price",row.get("price").getAsString(),row.get("currency").getAsString()).append(Text.literal("\n")).append(description)));addDrawableChild(button);
        }
        int bottom=top+h-57;
        addDrawableChild(FantasyButton.builder(Text.literal("‹"),b->{page=Math.max(0,page-1);clearAndInit();}).dimensions(left+12,bottom,24,20).build());
        addDrawableChild(FantasyButton.builder(Text.literal("›"),b->{if((page+1)*visible<rows.size())page++;clearAndInit();}).dimensions(left+40,bottom,24,20).build());
        int actionWidth=Math.max(50,(w-92)/3);
        addDrawableChild(FantasyButton.builder(Text.translatable("fantasyhub.slot",slot),b->{slot=slot%6+1;clearAndInit();}).dimensions(left+70,bottom,actionWidth,20).build());
        var row=selection();boolean owned=row!=null&&row.get("owned").getAsBoolean();
        var purchase=FantasyButton.builder(Text.translatable("fantasyhub.shop.purchase"),b->{if(selection()==null)return;var offer=selection();client.setScreen(new ConfirmScreen(yes->{if(yes)FantasyHubClient.send("purchase",offer.get("id").getAsString(),0);client.setScreen(this);},Text.translatable("fantasyhub.shop.confirm"),Text.translatable("fantasyhub.shop.confirm.detail",name(offer),offer.get("price").getAsString(),offer.get("currency").getAsString())));}).dimensions(left+74+actionWidth,bottom,actionWidth,20).build();purchase.active=row!=null&&!owned;addDrawableChild(purchase);
        var equip=FantasyButton.builder(Text.translatable("fantasyhub.equip"),b->FantasyHubClient.send("bind",selection().get("skillId").getAsString(),slot)).dimensions(left+78+actionWidth*2,bottom,actionWidth,20).build();equip.active=owned;addDrawableChild(equip);
        var cast=FantasyButton.builder(Text.translatable("fantasyhub.shop.cast"),b->FantasyHubClient.send("cast_id",selection().get("skillId").getAsString(),0)).dimensions(left+12,top+h-29,100,20).build();cast.active=owned;addDrawableChild(cast);
        addDrawableChild(FantasyButton.builder(Text.translatable("gui.done"),b->close()).dimensions(left+w-84,top+h-29,72,20).build());
    }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        renderBackground(context,mouseX,mouseY,delta);FantasyPanel.frame(context,left,top,w,h);context.drawCenteredTextWithShadow(textRenderer,title,left+w/2,top+12,0xffe7c37d);
        var row=selection();if(row!=null){
            int x=left+w/2+8,available=w/2-20,y=top+58-detailScroll;
            context.enableScissor(x,top+56,left+w-10,top+h-62);
            context.drawText(textRenderer,textRenderer.trimToWidth(name(row).getString(),available),x,y,0xffe7c37d,false);y+=13;
            Text classification=pokemonType(row.get("type").getAsString()).append(Text.literal(" · ")).append(Text.translatable("fantasyhub.shop.category."+row.get("category").getAsString()));
            context.drawText(textRenderer,textRenderer.trimToWidth(classification.getString(),available),x,y,0xffd5ae66,false);y+=13;
            context.drawText(textRenderer,textRenderer.trimToWidth(Text.translatable("fantasyhub.shop.price",row.get("price").getAsString(),row.get("currency").getAsString()).getString(),available),x,y,0xffeee2c8,false);y+=14;
            context.drawText(textRenderer,Text.translatable(row.get("owned").getAsBoolean()?"fantasyhub.shop.owned":"fantasyhub.shop.not_owned"),x,y,0xffd5ae66,false);y+=14;
            Text details=Text.translatable("fantasyhub.shop.details",row.get("mana").getAsDouble(),row.get("stamina").getAsDouble(),row.get("cooldown").getAsDouble(),row.get("range").getAsDouble(),row.get("requiredLevel").getAsInt());
            for(var line:textRenderer.wrapLines(details,available)){context.drawText(textRenderer,line,x,y,0xffeee2c8,false);y+=11;}
            context.disableScissor();
        }
        // Screen.render applies the background blur again in 1.21.1. Render the
        // registered widgets directly so our panel text is never blurred.
        for(var child:children())if(child instanceof net.minecraft.client.gui.Drawable drawable)drawable.render(context,mouseX,mouseY,delta);
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        if(mouseX>left+w/2&&selection()!=null){detailScroll=Math.max(0,Math.min(120,detailScroll-(int)(vertical*12)));return true;}
        var rows=rows();page=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/visible()),page-(int)Math.signum(vertical)));clearAndInit();return true;
    }
    @Override public boolean shouldPause(){return false;}
}
