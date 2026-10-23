package vn.svframe.fantasyhub.client;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.text.Text;
/** Server-authored catalog and prices; confirmation transmits only a short-lived offer token and payment choice. */
public final class FantasyShopScreen extends Screen {
 private static final List<String> CATEGORIES=List.of("classes","bundles","items","tickets","materials","titles","seasonal");
 private static final List<String> NAMES=List.of("Classes","Class Bundles","BeastCoin Item Shop","Boss Tickets","RPG Upgrade Materials","Titles & Privileges","Seasonal Exclusives");
 private String category="classes",classFilter="",selected="";private int page,left,top,w,h;private boolean requested;private int confirming;
 public FantasyShopScreen(){super(Text.literal("Fantasy Shop"));}
 public FantasyShopScreen(String cat,String id){this();category=cat;classFilter=id;}
 private JsonObject shop(){var s=FantasyHubClient.state().getAsJsonObject("fantasyShop");return s==null?new JsonObject():s;}
 private String str(String key){var s=shop();return s.has(key)?s.get(key).getAsString():"—";}
 private List<JsonObject> rows(){var arr=shop().getAsJsonArray("offers");if(arr==null)return List.of();var out=new ArrayList<JsonObject>();for(var e:arr){var r=e.getAsJsonObject();if((r.get("category").getAsString().equals(category)||(category.equals("materials")&&r.get("product").getAsString().contains("material"))||(category.equals("materials")&&r.get("product").getAsString().equals("special_catalyst"))||(category.equals("titles")&&r.get("product").getAsString().equals("cosmic_title"))||(category.equals("tickets")&&r.get("product").getAsString().startsWith("personal_"))||(category.equals("seasonal")&&r.get("product").getAsString().startsWith("seasonal_")))&&(classFilter.isBlank()||r.get("classId").getAsString().equals(classFilter)))out.add(r);}return out;}
 private JsonObject selection(){return rows().stream().filter(r->r.get("id").getAsString().equals(selected)).findFirst().orElse(null);}
 public void refresh(){clearAndInit();}
 private int count(){return Math.max(1,(h-118)/23);}
 @Override protected void init(){w=Math.min(width-12,620);h=Math.min(height-12,370);left=(width-w)/2;top=(height-h)/2;
  if(!requested){requested=true;FantasyHubClient.send("refresh","",0);}
  int ci=CATEGORIES.indexOf(category);addDrawableChild(FantasyButton.builder(Text.literal("‹"),b->{category=CATEGORIES.get(Math.floorMod(ci-1,CATEGORIES.size()));page=0;classFilter="";confirming=0;clearAndInit();}).dimensions(left+8,top+30,24,20).build());
  addDrawableChild(FantasyButton.builder(Text.literal(NAMES.get(ci)+" ›"),b->{category=CATEGORIES.get((ci+1)%CATEGORIES.size());page=0;classFilter="";confirming=0;clearAndInit();}).dimensions(left+36,top+30,w-44,20).build());
  var list=rows();page=Math.min(page,Math.max(0,(list.size()-1)/count()));int split=w/3;
  for(int i=0;i<count()&&page*count()+i<list.size();i++){var row=list.get(page*count()+i);addDrawableChild(FantasyButton.builder(FantasyHubClient.name(row.get("name").getAsString()),b->{selected=row.get("id").getAsString();confirming=0;clearAndInit();}).dimensions(left+8,top+58+i*23,split-16,21).build());}
  addDrawableChild(FantasyButton.builder(Text.literal("‹"),b->{page=Math.max(0,page-1);clearAndInit();}).dimensions(left+8,top+h-53,24,20).build());addDrawableChild(FantasyButton.builder(Text.literal("›"),b->{if((page+1)*count()<rows().size())page++;clearAndInit();}).dimensions(left+36,top+h-53,24,20).build());
  var row=selection();for(int method=1;method<=2;method++){final int m=method;String currency=m==1?"BeastCoin":"HunterCoin";String label=row==null?currency:row.get(m==1?"beast":"hunter").getAsString()+" "+currency;
   var buy=FantasyButton.builder(Text.literal((confirming==m?"Confirm / Xác nhận ":"Buy / Mua ")+label),b->{if(confirming!=m){confirming=m;clearAndInit();}else{FantasyHubClient.send("shop_buy",selected,m);confirming=0;clearAndInit();}}).dimensions(left+split+4+(m-1)*(w-split-12)/2,top+h-53,(w-split-16)/2,20).build();
   buy.active=row!=null&&row.get("available").getAsBoolean()&&shop().has(m==1?"beastAvailable":"hunterAvailable")&&shop().get(m==1?"beastAvailable":"hunterAvailable").getAsBoolean();addDrawableChild(buy);
  }
  addDrawableChild(FantasyButton.builder(Text.literal("Mailbox / Nhận đồ ("+str("mailCount")+")"),b->{FantasyHubClient.send("shop_claim","",0);}).dimensions(left+8,top+h-27,w/2-16,20).build());
  addDrawableChild(FantasyButton.builder(Text.literal("Refresh"),b->FantasyHubClient.send("refresh","",0)).dimensions(left+w/2,top+h-27,80,20).build());addDrawableChild(FantasyButton.builder(Text.translatable("gui.done"),b->close()).dimensions(left+w-80,top+h-27,72,20).build());
 }
 @Override public void render(DrawContext c,int mx,int my,float dt){renderBackground(c,mx,my,dt);c.draw();FantasyPanel.frame(c,left,top,w,h);c.drawCenteredTextWithShadow(textRenderer,title,left+w/2,top+8,0xffe7c37d);c.drawCenteredTextWithShadow(textRenderer,Text.literal("BeastCoin: "+str("beastBalance")+"  |  HunterCoin: "+str("hunterBalance")),left+w/2,top+20,0xffa8e9e6);
  var row=selection();int x=left+w/3+8,y=top+60; c.enableScissor(x,top+54,left+w-6,top+h-58);
  if(row!=null){try{var icon=ItemStack.fromNbt(client.world.getRegistryManager(),StringNbtReader.parse(row.get("icon").getAsString())).orElse(ItemStack.EMPTY);c.drawItem(icon,left+w-30,y);}catch(Exception ignored){}
   for(String field:List.of("name","description")){for(var line:textRenderer.wrapLines(FantasyHubClient.name(row.get(field).getAsString()),w-w/3-45)){c.drawText(textRenderer,line,x,y,0xffeeeeee,false);y+=11;}y+=6;}
   for(var e:row.getAsJsonArray("contents"))for(var line:textRenderer.wrapLines(Text.literal(e.getAsString()),w-w/3-24)){c.drawText(textRenderer,line,x,y,0xffc3d9bd,false);y+=11;}
   y+=8;c.drawText(textRenderer,Text.literal("Limit: "+row.get("limit").getAsInt()+" (0 = unlimited) | Purchased: "+row.get("bought").getAsInt()),x,y,0xffd9c49c,false);
  }else c.drawText(textRenderer,Text.literal("Select a product / Chọn sản phẩm"),x,y,0xffcccccc,false);
  c.disableScissor();FantasyPanel.widgets(this,c,mx,my,dt);
 }
}
