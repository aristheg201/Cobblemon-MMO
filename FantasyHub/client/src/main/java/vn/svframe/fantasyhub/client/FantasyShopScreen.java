package vn.svframe.fantasyhub.client;

import com.google.gson.*;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.component.DataComponentTypes;

/** Native item previews only. Navigation, tooltips and confirmations never spend currency. */
public final class FantasyShopScreen extends Screen {
 private static final String KEY="fantasyhub.commerce.";
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("FantasyHub/Shop");
 private final Screen parent;
 private ShopView.Tab tab=ShopView.Tab.CLASS;
 private String selected="",classFilter="",confirmation="";
 private int page,detailScroll,maxScroll,previewIndex;
 private boolean requested;
 private ShopView.Layout layout;
 private final Map<String,Visual> visuals=new HashMap<>();
 private final List<GearHit> gearHits=new ArrayList<>();
 private ItemStack hoveredStack=ItemStack.EMPTY;
 private Text hoveredText;
 private record Visual(ItemStack stack,boolean valid){}
 private record GearHit(ShopView.Box box,int index){}
 public FantasyShopScreen(){this(new HubScreen(HubScreen.View.PROFILE));}
 public FantasyShopScreen(Screen parent){super(Text.translatable(KEY+"title"));this.parent=parent;}
 @Override public void close(){client.setScreen(parent);}
 public FantasyShopScreen(String category,String id){this(new HubScreen(HubScreen.View.PROFILE),category,id);}
 public FantasyShopScreen(Screen parent,String category,String id){this(parent);tab=category.equals("bundles")?ShopView.Tab.BUNDLE:category.equals("classes")?ShopView.Tab.CLASS:ShopView.Tab.SHOP;classFilter=id;}
 private static Text tr(String key,Object... args){return Text.translatable(KEY+key,args);}
 private static String string(JsonObject row,String key,String fallback){return row.has(key)?row.get(key).getAsString():fallback;}
 private static boolean flag(JsonObject row,String key){return row.has(key)&&row.get(key).getAsBoolean();}
 private JsonObject shop(){var value=FantasyHubClient.state().getAsJsonObject("fantasyShop");return value==null?new JsonObject():value;}
 private List<JsonObject> rows(){return ShopView.rows(shop(),tab).stream().filter(r->classFilter.isBlank()||string(r,"classId","").equals(classFilter)).toList();}
 private JsonObject selection(){return rows().stream().filter(r->selected.equals(string(r,"product",""))).findFirst().orElse(null);}
 private Text className(JsonObject row){String key=KEY+"class."+string(row,"classId","").toLowerCase(Locale.ROOT);return I18n.hasTranslation(key)?Text.translatable(key):FantasyHubClient.name(string(row,"name",""));}
 private Text name(JsonObject row){if(!string(row,"classId","").isBlank()){var name=className(row);return string(row,"category","").equals("bundles")?tr(flag(row,"equipmentOnly")?"bundle_equipment":"bundle_class",name):name;}String key=KEY+"offer."+string(row,"product","")+".name";return I18n.hasTranslation(key)?Text.translatable(key):FantasyHubClient.name(string(row,"name",""));}
 private Text description(JsonObject row){if(tab==ShopView.Tab.CLASS)return tr(flag(row,"owned")?"class_owned_description":flag(shop(),"freeChoice")?"class_free_description":"class_purchase_description");if(tab==ShopView.Tab.BUNDLE)return tr(flag(row,"equipmentOnly")?"equipment_description":"bundle_description");String key=KEY+"offer."+string(row,"product","")+".description";return I18n.hasTranslation(key)?Text.translatable(key):FantasyHubClient.name(string(row,"description",""));}
 private String money(String raw){try{return ShopView.amount(raw);}catch(NumberFormatException e){return tr("unavailable").getString();}}
 private Text price(JsonObject row,int method){return Text.literal(money(string(row,method==1?"beast":"hunter","0"))+" "+(method==1?"BeastCoin":"HunterCoin"));}
 private String signature(JsonObject row,int method){return string(row,"id","")+":"+method+":"+string(row,"beast","")+":"+string(row,"hunter","")+":"+flag(row,"owned")+":"+flag(row,"active");}
 public void refresh(){var row=selection();if(row==null||(!confirmation.isBlank()&&!confirmation.equals(signature(row,1))&&!confirmation.equals(signature(row,2))))confirmation="";clearAndInit();}
 private int count(){return Math.max(1,layout.list().h()/28);}
 private void select(JsonObject row){selected=string(row,"product","");detailScroll=0;previewIndex=0;confirmation="";clearAndInit();}
 private void switchTab(ShopView.Tab next){if(next==tab)return;tab=next;classFilter="";selected="";confirmation="";page=0;detailScroll=0;previewIndex=0;clearAndInit();}
 @Override protected void init(){
  layout=ShopView.layout(width,height);
  if(!requested){requested=true;FantasyHubClient.send("refresh","",0);}
  if(selection()==null){selected="";detailScroll=0;previewIndex=0;confirmation="";}
  var tabs=layout.tabs();int tw=(tabs.w()-8)/3;
  for(var t:ShopView.Tab.values()){var button=button(new ShopView.Box(tabs.x()+t.ordinal()*(tw+4),tabs.y(),t==ShopView.Tab.BUNDLE?tabs.w()-2*(tw+4):tw,tabs.h()),tr("tab_label."+t.name().toLowerCase(Locale.ROOT)),()->switchTab(t),t==tab);button.setTooltip(Tooltip.of(tr("tab."+t.name().toLowerCase(Locale.ROOT))));}
  var list=rows();page=Math.min(page,Math.max(0,(list.size()-1)/count()));
  for(int i=0;i<count()&&page*count()+i<list.size();i++){
   var row=list.get(page*count()+i);var box=new ShopView.Box(layout.list().x(),layout.list().y()+i*28,layout.list().w(),26);
   var b=new ShopButton(box,name(row),()->select(row),selected.equals(string(row,"product","")),row);b.setTooltip(Tooltip.of(name(row).copy().append("\n").append(subtitle(row))));addDrawableChild(b);
  }
  var p=layout.panel();int py=layout.hunter().y();
  var prev=button(new ShopView.Box(layout.list().x(),py,22,layout.hunter().h()),Text.literal("‹"),()->{page=Math.max(0,page-1);clearAndInit();},false);prev.active=page>0;prev.setTooltip(Tooltip.of(tr("previous")));
  var next=button(new ShopView.Box(layout.list().right()-22,py,22,layout.hunter().h()),Text.literal("›"),()->{page++;clearAndInit();},false);next.active=(page+1)*count()<list.size();next.setTooltip(Tooltip.of(tr("next")));
  var row=selection();for(int method=1;method<=2;method++){
   final int m=method;Text label=tr("select_product");boolean enabled=false;
   if(row!=null){boolean owned=tab==ShopView.Tab.CLASS&&flag(row,"owned"),free=tab==ShopView.Tab.CLASS&&flag(shop(),"freeChoice");long remaining=remaining();
    if(owned||free){if(flag(row,"active"))label=tr("active");else if(owned&&remaining>0){label=m==1?tr("wait",remaining):tr("bypass",money(string(shop(),"bypassBeast","500")));enabled=m==2&&flag(shop(),"beastAvailable");}else{label=m==1?tr(free&&!owned?"choose_free":"switch_free"):tr("owned");enabled=m==1;}}
    else{label=tr("buy",price(row,m));enabled=flag(row,"available")&&flag(shop(),m==1?"beastAvailable":"hunterAvailable");}
    if(confirmation.equals(signature(row,m)))label=tr("confirm",label);
    if(!(tab==ShopView.Tab.CLASS&&(flag(row,"owned")||flag(shop(),"freeChoice"))))enabled&=valid(row);
   }
   var b=button(m==1?layout.beast():layout.hunter(),label,()->activate(m),false);b.active=enabled;b.setTooltip(Tooltip.of(!enabled&&row!=null&&m==2&&flag(row,"owned")&&remaining()>0&&!flag(shop(),"beastAvailable")?tr("beast_unavailable"):label));
  }
  var f=layout.footer();int left=layout.list().w(),right=layout.preview().w(),rx=layout.preview().x();
  button(new ShopView.Box(f.x(),f.y(),left,f.h()),tr("mailbox",string(shop(),"mailCount","0")),()->FantasyHubClient.send("shop_claim","",0),false);
  button(new ShopView.Box(rx,f.y(),(right-4)/2,f.h()),tr("refresh"),()->{confirmation="";FantasyHubClient.send("refresh","",0);},false);
  button(new ShopView.Box(rx+(right-4)/2+4,f.y(),right-(right-4)/2-4,f.h()),Text.translatable("gui.back"),this::close,false);
 }
 private long remaining(){if(shop().has("switchAvailableAt"))return Math.max(0,(shop().get("switchAvailableAt").getAsLong()-System.currentTimeMillis()+999)/1000);return shop().has("switchRemainingSeconds")?shop().get("switchRemainingSeconds").getAsLong():0;}
 private void activate(int method){var row=selection();if(row==null||(!(tab==ShopView.Tab.CLASS&&(flag(row,"owned")||flag(shop(),"freeChoice"))))&&!valid(row))return;String signature=signature(row,method);if(!confirmation.equals(signature)){confirmation=signature;clearAndInit();return;}confirmation="";
  if(tab==ShopView.Tab.CLASS&&(flag(row,"owned")||flag(shop(),"freeChoice")))FantasyHubClient.send(flag(row,"owned")&&remaining()>0?"switch_bypass":"switch_class",string(row,"classId",""),0);
  else FantasyHubClient.send("shop_buy",string(row,"id",""),method);
 }
 private Text subtitle(JsonObject row){if(string(row,"category","").equals("classes"))return tr(flag(row,"active")?"active":flag(row,"owned")?(remaining()>0?"owned_cooldown":"owned_ready"):flag(shop(),"freeChoice")?"free_choice":"purchase_available");if(string(row,"category","").equals("bundles"))return tr(flag(row,"equipmentOnly")?"equipment_only":"class_equipment");return Text.literal(money(string(row,"beast","0"))+" BC · "+money(string(row,"hunter","0"))+" HC");}
 private Visual visual(String encoded){return visuals.computeIfAbsent(encoded,key->{ItemStack stack=ItemStack.EMPTY;String registryId="unknown";try{registryId=StringNbtReader.parse(key).getString("id");
   stack=ItemStack.fromNbt(client.world.getRegistryManager(),StringNbtReader.parse(key)).orElseThrow(()->new IllegalArgumentException("Unknown item in preview NBT"));if(stack.isEmpty())throw new IllegalArgumentException("Empty item preview");
   String id=Registries.ITEM.getId(stack.getItem()).toString();var custom=stack.get(DataComponentTypes.CUSTOM_DATA);String model=custom==null?"":custom.copyNbt().getString("SVFRAME_ITEM_MODEL");if(model.isBlank())model=Registries.ITEM.getId(stack.getItem()).getNamespace()+":item/"+Registries.ITEM.getId(stack.getItem()).getPath();var modelId=Identifier.of(model);String expected="assets/"+modelId.getNamespace()+"/models/"+modelId.getPath()+".json";
   var baked=client.getItemRenderer().getModel(stack,client.world,client.player,0);if(baked==client.getBakedModelManager().getMissingModel())throw new IllegalArgumentException("Missing model for "+id+"; expected "+expected);
   if(stack.getName().getString().equals(stack.getTranslationKey())&&!I18n.hasTranslation(stack.getTranslationKey()))throw new IllegalArgumentException("Missing item translation for "+id+": "+stack.getTranslationKey()+" in assets/"+Registries.ITEM.getId(stack.getItem()).getNamespace()+"/lang/"+client.options.language+".json");
   return new Visual(stack,true);
  }catch(Exception e){LOG.error("Shop preview disabled [{}]: {}",registryId,e.getMessage());return new Visual(stack,false);}});}
 private List<Visual> equipment(JsonObject row){var result=new ArrayList<Visual>();if(row.has("items"))for(var item:row.getAsJsonArray("items"))result.add(visual(item.getAsString()));return result;}
 private boolean valid(JsonObject row){return visual(string(row,"icon","{}")).valid&&equipment(row).stream().allMatch(Visual::valid);}
 private ShopButton button(ShopView.Box box,Text label,Runnable action,boolean selected){return addDrawableChild(new ShopButton(box,label,action,selected,null));}
 private final class ShopButton extends ButtonWidget {
  final boolean chosen;final JsonObject product;
  ShopButton(ShopView.Box box,Text label,Runnable action,boolean chosen,JsonObject product){super(box.x(),box.y(),box.w(),box.h(),label,b->action.run(),DEFAULT_NARRATION_SUPPLIER);this.chosen=chosen;this.product=product;}
  @Override protected void renderWidget(DrawContext c,int mx,int my,float delta){
   FantasyPanel.frame(c,getX(),getY(),getWidth(),getHeight());if(chosen||isHovered()||isFocused()){c.fill(getX()+3,getY()+3,getX()+getWidth()-3,getY()+getHeight()-3,chosen?0xff63402d:0xff382a2c);if(chosen)c.fill(getX()+3,getY()+getHeight()-3,getX()+getWidth()-3,getY()+getHeight()-2,0xfff3d178);}
   int x=getX()+5,w=getWidth()-10;if(product!=null){var icon=visual(string(product,"icon","{}"));if(!icon.stack.isEmpty()){c.drawItem(icon.stack,x,getY()+5);x+=21;w-=21;}c.drawText(textRenderer,ShopView.fit(getMessage().getString(),w,textRenderer::getWidth),x,getY()+4,chosen?0xffffdfa0:0xffeddfc2,false);c.drawText(textRenderer,ShopView.fit(subtitle(product).getString(),w,textRenderer::getWidth),x,getY()+15,0xffb7b6a6,false);}
   else c.drawCenteredTextWithShadow(textRenderer,Text.literal(ShopView.fit(getMessage().getString(),w,textRenderer::getWidth)),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?0xffebd29a:0xff82745d);
  }
 }
 @Override public void render(DrawContext c,int mx,int my,float dt){
  renderBackground(c,mx,my,dt);c.draw();var p=layout.panel();FantasyPanel.frame(c,p.x(),p.y(),p.w(),p.h());hoveredStack=ItemStack.EMPTY;hoveredText=null;gearHits.clear();
  c.drawCenteredTextWithShadow(textRenderer,title,p.x()+p.w()/2,p.y()+7,0xffffd78b);
  String balances="BeastCoin: "+money(string(shop(),"beastBalance","0"))+"  |  HunterCoin: "+money(string(shop(),"hunterBalance","0"));c.drawCenteredTextWithShadow(textRenderer,Text.literal(ShopView.fit(balances,p.w()-18,textRenderer::getWidth)),p.x()+p.w()/2,p.y()+21,0xffa8e9e6);if(mx>=p.x()&&mx<p.right()&&my>=p.y()+19&&my<p.y()+32)hoveredText=Text.literal(balances);
  var row=selection();if(row!=null)renderDetails(c,row,mx,my);else{var b=layout.preview();drawWrapped(c,tr("select_product"),b.x()+8,b.y()+8,b.w()-16,0xffc6bba9);}
  int pages=Math.max(1,(rows().size()+count()-1)/count());c.drawCenteredTextWithShadow(textRenderer,Text.literal((page+1)+" / "+pages),layout.list().x()+layout.list().w()/2,layout.hunter().y()+6,0xffc3ab7a);
  FantasyPanel.widgets(this,c,mx,my,dt);
  if(!hoveredStack.isEmpty())c.drawItemTooltip(textRenderer,hoveredStack,mx,my);else if(hoveredText!=null)c.drawTooltip(textRenderer,hoveredText,mx,my);
 }
 private int drawWrapped(DrawContext c,Text text,int x,int y,int width,int color){for(var line:textRenderer.wrapLines(text,Math.max(12,width))){c.drawText(textRenderer,line,x,y,color,false);y+=11;}return y;}
 private void renderDetails(DrawContext c,JsonObject row,int mx,int my){
  var b=layout.preview();FantasyPanel.frame(c,b.x(),b.y(),b.w(),b.h());var gear=equipment(row);previewIndex=Math.min(previewIndex,Math.max(0,gear.size()-1));var preview=gear.isEmpty()?visual(string(row,"icon","{}")):gear.get(previewIndex);int size=Math.min(56,b.h()-12),ix=b.x()+7,iy=b.y()+(b.h()-size)/2;
  if(!preview.stack.isEmpty()){c.getMatrices().push();c.getMatrices().translate(ix,iy,0);c.getMatrices().scale(size/16f,size/16f,1);c.drawItem(preview.stack,0,0);c.getMatrices().pop();}
  int textX=ix+size+8,textW=b.right()-textX-6;c.enableScissor(textX,b.y()+4,b.right()-4,b.bottom()-3);drawWrapped(c,name(row),textX,b.y()+7,textW,0xffffdda1);c.disableScissor();
  if(b.contains(mx,my)){hoveredText=name(row);if(mx<ix+size+3&&preview.valid)hoveredStack=preview.stack;}
  var d=layout.details();FantasyPanel.frame(c,d.x(),d.y(),d.w(),d.h());c.enableScissor(d.x()+3,d.y()+3,d.right()-3,d.bottom()-3);
  int x=d.x()+7,y=d.y()+7-detailScroll,contentStart=y;y=drawWrapped(c,description(row),x,y,d.w()-20,0xffded6c6)+7;
  if(tab==ShopView.Tab.CLASS){y=drawWrapped(c,subtitle(row),x,y,d.w()-20,0xffd8b66e)+5;if(flag(row,"owned")&&remaining()>0)y=drawWrapped(c,tr("cooldown_detail",remaining(),money(string(shop(),"bypassBeast","500"))),x,y,d.w()-20,0xffc6bba9)+5;}
  if(!valid(row))y=drawWrapped(c,tr("invalid_preview"),x,y,d.w()-20,0xffff8888)+6;
  if(!gear.isEmpty()){y=drawWrapped(c,tr("contents"),x,y,d.w()-20,0xffdcb771)+4;for(int i=0;i<gear.size();i++){
   var item=gear.get(i);int rh=23;boolean hovered=d.contains(mx,my)&&my>=y&&my<y+rh;
   if(i==previewIndex||hovered)c.fill(x-2,y-1,d.right()-7,y+rh-2,hovered?0xff43302b:0xff28221e);
   if(!item.stack.isEmpty())c.drawItem(item.stack,x,y+2);String label="×"+item.stack.getCount()+" "+(item.valid?item.stack.getName().getString():tr("invalid_item").getString());
   c.drawText(textRenderer,ShopView.fit(label,d.w()-46,textRenderer::getWidth),x+22,y+6,0xffcbdcbb,false);gearHits.add(new GearHit(new ShopView.Box(x-2,y-1,d.w()-12,rh),i));
   if(hovered){if(item.valid)hoveredStack=item.stack;else hoveredText=tr("invalid_preview");}y+=rh;
  }}
  int limit=row.has("limit")?row.get("limit").getAsInt():0,bought=row.has("bought")?row.get("bought").getAsInt():0;
  if(tab==ShopView.Tab.SHOP){y+=6;y=drawWrapped(c,limit>0?tr("limit",bought,limit):tr("unlimited"),x,y,d.w()-20,0xffaaa08b);}
  maxScroll=Math.max(0,y-contentStart-(d.h()-14));detailScroll=Math.min(detailScroll,maxScroll);c.disableScissor();
  if(maxScroll>0){int track=d.h()-8,thumb=Math.max(8,track*track/(track+maxScroll));int sy=d.y()+4+(track-thumb)*detailScroll/maxScroll;c.fill(d.right()-5,d.y()+4,d.right()-3,d.bottom()-4,0xff302821);c.fill(d.right()-5,sy,d.right()-3,sy+thumb,0xffc6a15b);}
 }
 JsonObject observation(){
  var o=new JsonObject();o.addProperty("tab",tab.name());o.addProperty("product",selected);o.addProperty("language",client.options.language);o.addProperty("guiScale",client.getWindow().getScaleFactor());o.addProperty("scaledWidth",width);o.addProperty("scaledHeight",height);o.addProperty("page",page);o.addProperty("scroll",detailScroll);o.addProperty("maxScroll",maxScroll);o.addProperty("previewIndex",previewIndex);
  var items=new JsonArray();var row=selection();if(row!=null)for(var v:equipment(row)){var item=new JsonObject();item.addProperty("id",Registries.ITEM.getId(v.stack.getItem()).toString());item.addProperty("name",v.stack.getName().getString());item.addProperty("valid",v.valid);items.add(item);}o.add("equipment",items);return o;
 }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(layout.details().contains(x,y)){detailScroll=Math.clamp(detailScroll-(int)(vertical*22),0,maxScroll);return true;}if(layout.list().contains(x,y)){int max=Math.max(0,(rows().size()-1)/count());page=Math.clamp(page-(int)Math.signum(vertical),0,max);clearAndInit();return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
 @Override public boolean mouseClicked(double x,double y,int button){if(button==0&&layout.details().contains(x,y))for(var hit:gearHits)if(hit.box.contains(x,y)){previewIndex=hit.index;return true;}return super.mouseClicked(x,y,button);}
}
