package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.text.Text;

/** Existing RPG items, server-quoted rules and native upgrade results inside Fantasy HUD. */
public final class ItemUpgradeScreen extends Screen {
 private static final String KEY="fantasyhub.item_upgrade.";
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("FantasyHub/UpgradePreview");
 private final Screen parent;
 private ShopView.Layout layout;
 private int selected=-1,page,scroll,maxScroll;
 private String confirmation="";
 private boolean requested;
 private ItemStack hovered=ItemStack.EMPTY;
 private final Map<String,ItemStack> icons=new HashMap<>();
 public ItemUpgradeScreen(Screen parent){super(tr("title"));this.parent=parent;}
 private static Text tr(String key,Object...args){return Text.translatable(KEY+key,args);}
 private JsonObject state(){var s=FantasyHubClient.state().getAsJsonObject("itemUpgrades");return s==null?new JsonObject():s;}
 private List<JsonObject> rows(){if(!state().has("items"))return List.of();var rows=new ArrayList<JsonObject>();for(var r:state().getAsJsonArray("items"))rows.add(r.getAsJsonObject());return rows;}
 private JsonObject selection(){return rows().stream().filter(r->r.get("slot").getAsInt()==selected).findFirst().orElse(null);}
 private ItemStack stack(String raw){return icons.computeIfAbsent(raw,s->{try{return ItemStack.fromNbt(client.world.getRegistryManager(),StringNbtReader.parse(s)).orElse(ItemStack.EMPTY);}catch(Exception e){LOG.error("Invalid native equipment preview in Fantasy HUD",e);return ItemStack.EMPTY;}});}
 public void refresh(){var row=selection();if(row==null||!confirmation.equals(row.get("quote").getAsString()))confirmation="";clearAndInit();}
 private int count(){return Math.max(1,layout.list().h()/28);}
 private net.minecraft.client.gui.widget.ButtonWidget button(ShopView.Box box,Text text,Runnable action){return addDrawableChild(FantasyButton.builder(text,b->action.run()).dimensions(box.x(),box.y(),box.w(),box.h()).build());}
 @Override protected void init(){
  layout=ShopView.layout(width,height);if(!requested){requested=true;FantasyHubClient.send("refresh","",0);}
  var rows=rows();page=Math.min(page,Math.max(0,(rows.size()-1)/count()));
  for(int i=0;i<count()&&page*count()+i<rows.size();i++){
   var row=rows.get(page*count()+i);var item=stack(row.get("item").getAsString());int y=layout.list().y()+i*28;
   var b=button(new ShopView.Box(layout.list().x()+23,y,layout.list().w()-23,25),Text.literal(ShopView.fit(item.getName().getString(),layout.list().w()-35,textRenderer::getWidth)),()->{selected=row.get("slot").getAsInt();confirmation="";scroll=0;clearAndInit();});b.setTooltip(Tooltip.of(item.getName()));
  }
  var row=selection();boolean enabled=row!=null&&row.get("blocked").getAsString().isBlank()&&!stack(row.get("item").getAsString()).isEmpty()&&state().has("catalysts")&&state().get("catalysts").getAsInt()>0;
  var buy=button(layout.beast(),tr(confirmation.isBlank()?"attempt":"confirm"),()->{var r=selection();if(r==null)return;String token=r.get("quote").getAsString();if(!confirmation.equals(token)){confirmation=token;clearAndInit();return;}confirmation="";FantasyHubClient.send("gear_upgrade",token,0);});buy.active=enabled;
  var p=button(new ShopView.Box(layout.list().x(),layout.hunter().y(),22,20),Text.literal("‹"),()->{page=Math.max(0,page-1);clearAndInit();});p.active=page>0;
  var n=button(new ShopView.Box(layout.list().right()-22,layout.hunter().y(),22,20),Text.literal("›"),()->{page++;clearAndInit();});n.active=(page+1)*count()<rows.size();
  button(layout.hunter(),tr("refresh"),()->{confirmation="";FantasyHubClient.send("refresh","",0);});
  var f=layout.footer();button(new ShopView.Box(f.right()-96,f.y(),96,f.h()),Text.translatable("gui.back"),this::close);
 }
 @Override public void close(){client.setScreen(parent);}
 private int wrapped(DrawContext c,Text text,int x,int y,int w,int color){for(var line:textRenderer.wrapLines(text,w)){c.drawText(textRenderer,line,x,y,color,false);y+=11;}return y;}
 @Override public void render(DrawContext c,int mx,int my,float delta){
  renderBackground(c,mx,my,delta);var p=layout.panel();FantasyPanel.frame(c,p.x(),p.y(),p.w(),p.h());c.drawCenteredTextWithShadow(textRenderer,title,width/2,p.y()+8,0xfff2cf83);
  c.drawCenteredTextWithShadow(textRenderer,tr("catalysts",state().has("catalysts")?state().get("catalysts").getAsInt():0),width/2,p.y()+23,0xff8adbd4);
  c.drawText(textRenderer,tr("inventory"),layout.list().x(),layout.tabs().y()+5,0xffeddfc2,false);
  hovered=ItemStack.EMPTY;var rows=rows();for(int i=0;i<count()&&page*count()+i<rows.size();i++){
   var row=rows.get(page*count()+i);int x=layout.list().x()+3,y=layout.list().y()+i*28+5;var item=stack(row.get("item").getAsString());c.drawItem(item,x,y);if(mx>=x&&mx<x+19&&my>=y&&my<y+19)hovered=item;
   if(row.get("slot").getAsInt()==selected)c.fill(x-3,y-4,x-1,y+19,0xffffcf70);
  }
  var row=selection();if(row==null)wrapped(c,tr(rows.isEmpty()?"empty":"select"),layout.preview().x()+8,layout.preview().y()+8,layout.preview().w()-16,0xffd9c6a3);
  else{
   var box=layout.preview();FantasyPanel.frame(c,box.x(),box.y(),box.w(),box.h());var item=stack(row.get("item").getAsString());float scale=Math.min(3f,(box.h()-10)/16f);c.getMatrices().push();c.getMatrices().translate(box.x()+8,box.y()+5,100);c.getMatrices().scale(scale,scale,1);c.drawItem(item,0,0);c.getMatrices().pop();
   int ix=box.x()+16+(int)(16*scale);c.drawText(textRenderer,ShopView.fit(item.getName().getString(),Math.max(24,box.right()-ix-8),textRenderer::getWidth),ix,box.y()+8,0xffffda88,false);
   c.drawText(textRenderer,tr("level",row.get("level").getAsInt(),row.get("max").getAsInt()),ix,box.bottom()-15,0xffddd4bc,false);
   if(box.contains(mx,my))hovered=item;
   var d=layout.details();int x=d.x()+6,y=d.y()+5-scroll;c.enableScissor(d.x(),d.y(),d.right(),d.bottom());
   String blocked=row.get("blocked").getAsString();if(!blocked.isBlank())y=wrapped(c,tr("error."+blocked),x,y,d.w()-12,0xffff9090)+5;
   y=wrapped(c,tr("chance",String.format(Locale.ROOT,"%.1f",row.get("chance").getAsDouble()*100)),x,y,d.w()-12,0xffb9e599)+5;
   y=wrapped(c,tr("catalyst_cost"),x,y,d.w()-12,0xffe9cd8c)+4;
   for(var raw:row.getAsJsonArray("costs")){
    var cost=raw.getAsJsonObject();Text name=cost.has("item")?stack(cost.get("item").getAsString()).getName():Text.literal(cost.get("provider").getAsString()+":"+cost.get("id").getAsString());
    y=wrapped(c,tr("cost",cost.get("amount").getAsInt(),name),x,y,d.w()-12,0xffded6c4)+3;
   }
   y=wrapped(c,tr(row.get("destroyOnFail").getAsBoolean()?"risk_destroy":"risk_keep"),x,y+4,d.w()-12,0xfff3b49e)+4;
   maxScroll=Math.max(0,y+scroll-d.bottom()+6);c.disableScissor();
   if(maxScroll>0){int h=Math.max(8,d.h()*d.h()/(d.h()+maxScroll)),sy=d.y()+(d.h()-h)*scroll/maxScroll;c.fill(d.right()-3,d.y(),d.right()-1,d.bottom(),0xff392d23);c.fill(d.right()-3,sy,d.right()-1,sy+h,0xffd5b574);}
  }
  c.drawText(textRenderer,Text.literal((page+1)+" / "+Math.max(1,(rows.size()+count()-1)/count())),layout.list().x()+32,layout.hunter().y()+6,0xffddc48d,false);
  FantasyPanel.widgets(this,c,mx,my,delta);if(!hovered.isEmpty())c.drawItemTooltip(textRenderer,hovered,mx,my);
 }
 @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(layout.details().contains(mx,my)){scroll=Math.clamp(scroll-(int)(vertical*22),0,maxScroll);return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
}
