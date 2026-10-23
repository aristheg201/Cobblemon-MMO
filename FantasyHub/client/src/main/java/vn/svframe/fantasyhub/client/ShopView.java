package vn.svframe.fantasyhub.client;
import com.google.gson.*;
import java.util.*;
import java.util.function.ToIntFunction;
/** Screen geometry and catalog filtering have no Minecraft or currency side effects. */
final class ShopView {
 enum Tab { CLASS, SHOP, BUNDLE }
 record Box(int x,int y,int w,int h){int right(){return x+w;}int bottom(){return y+h;}boolean contains(double x,double y){return x>=this.x&&x<right()&&y>=this.y&&y<bottom();}}
 record Layout(Box panel,Box tabs,Box list,Box preview,Box details,Box beast,Box hunter,Box footer){}
 static Layout layout(int width,int height){
  int w=Math.min(760,width-12),h=Math.min(440,height-12),x=(width-w)/2,y=(height-h)/2;
  int lw=Math.max(104,Math.min(230,(w-24)*34/100)),rx=x+lw+16,rw=x+w-8-rx;
  boolean compact=h<210;int body=compact?53:64,ph=compact?20:Math.max(38,Math.min(76,h-188)),dy=y+body+ph+4;
  int purchase=compact?62:76,gap=compact?19:23,footer=compact?22:27,bh=compact?16:20;
  return new Layout(new Box(x,y,w,h),new Box(x+8,y+(compact?31:36),w-16,compact?18:22),new Box(x+8,y+body,lw,Math.max(24,h-body-(compact?48:58))),new Box(rx,y+body,rw,ph),new Box(rx,dy,rw,Math.max(12,y+h-purchase-6-dy)),new Box(rx,y+h-purchase,rw,bh),new Box(rx,y+h-purchase+gap,rw,bh),new Box(x+8,y+h-footer,w-16,bh));
 }
 static List<JsonObject> rows(JsonObject catalog,Tab tab){
  if(!catalog.has("offers"))return List.of();var out=new ArrayList<JsonObject>();
  for(var value:catalog.getAsJsonArray("offers")){var r=value.getAsJsonObject();String category=r.get("category").getAsString();if(switch(tab){case CLASS->category.equals("classes");case BUNDLE->category.equals("bundles");case SHOP->Set.of("items","materials","tickets","titles","seasonal").contains(category);})out.add(r);}return out;
 }
 static String fit(String value,int width,ToIntFunction<String> measure){
  if(measure.applyAsInt(value)<=width)return value;if(width<measure.applyAsInt("…"))return "";
  int end=value.length();while(end>0&&measure.applyAsInt(value.substring(0,end)+"…")>width)end=value.offsetByCodePoints(end,-1);return value.substring(0,end)+"…";
 }
 static String amount(String raw){var value=new java.math.BigDecimal(raw).stripTrailingZeros();var format=java.text.NumberFormat.getNumberInstance(Locale.US);format.setMaximumFractionDigits(Math.max(0,value.scale()));return format.format(value);}
}
