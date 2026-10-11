package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Resources share vanilla status-bar anchors, independent of GUI scale and maximum health. */
public final class CompactHud {
    private CompactHud(){}
    public static boolean active(){var client=MinecraftClient.getInstance();return FantasyHubClient.hudEnabled&&client.player!=null&&!client.options.hudHidden&&FantasyHubClient.state().has("schema");}
    public static boolean visible(String resource){return active()&&FantasyHubClient.options.visible(resource);}
    public static void render(DrawContext context){
        var client=MinecraftClient.getInstance();if(client.interactionManager==null||!client.interactionManager.hasStatusBars())return;
        int center=context.getScaledWindowWidth()/2,y=context.getScaledWindowHeight()-39;
        if(visible("health"))bar(context,center-91,y,"health",client.player.getHealth(),client.player.getMaxHealth(),0xffba3840);
        if(visible("stamina"))bar(context,center-91,y-10,"stamina",FantasyHubClient.number("stamina"),FantasyHubClient.number("staminaMax"),0xffb89f38);
        if(visible("mana"))bar(context,center+10,y-10,"mana",FantasyHubClient.number("mana"),FantasyHubClient.number("manaMax"),0xff347bbd);
        // A small charge jewel preserves the distinct magic resource without another panel or row.
        if(visible("magic")&&FantasyHubClient.number("magicMax")>0){int x=center-3;context.fill(x-1,y+1,x+7,y+9,0xff302437);int height=(int)Math.round(6*Math.clamp(FantasyHubClient.number("magic")/FantasyHubClient.number("magicMax"),0,1));context.fill(x,y+8-height,x+6,y+8,0xffa166c9);}
    }
    private static void bar(DrawContext c,int x,int y,String resource,double value,double max,int color){
        int width=81,height=9;
        c.fill(x,y,x+width,y+height,0xe5100d0e);c.fill(x+1,y+1,x+width-1,y+height-1,0xff32262b);
        int fill=(int)Math.round((width-2)*Math.clamp(max<=0?0:value/max,0,1));
        c.fill(x+1,y+1,x+1+fill,y+height-1,color);c.fill(x+1,y+1,x+1+fill,y+2,0xffd9bb88);
        var renderer=MinecraftClient.getInstance().textRenderer;
        String amount=number(value)+" / "+number(max);String label=FantasyHubClient.options.compact?amount:Text.translatable("fantasyhub.resource."+resource).getString()+" "+amount;
        float scale=Math.clamp(FantasyHubClient.hudScale*.8f,.6f,.8f);label=renderer.trimToWidth(label,(int)((width-6)/scale));
        c.getMatrices().push();c.getMatrices().translate(x+width/2f,y+(height-renderer.fontHeight*scale)/2f,0);c.getMatrices().scale(scale,scale,1);c.drawText(renderer,label,-renderer.getWidth(label)/2,0,0xfff9f0e0,true);c.getMatrices().pop();
    }
    private static String number(double value){return String.format(java.util.Locale.ROOT,"%.0f",Math.max(0,value));}
    public static void experience(DrawContext c,int x){int y=c.getScaledWindowHeight()-29;c.fill(x,y,x+182,y+5,0xff181710);double max=FantasyHubClient.number("nextLevel"),progress=max<=0?0:Math.clamp(FantasyHubClient.number("experience")/max,0,1);int fill=(int)Math.round(180*progress);c.fill(x+1,y+1,x+1+fill,y+4,0xff80bd3d);}
    public static void level(DrawContext c){var renderer=MinecraftClient.getInstance().textRenderer;String text=String.valueOf((int)FantasyHubClient.number("level"));int x=(c.getScaledWindowWidth()-renderer.getWidth(text))/2,y=c.getScaledWindowHeight()-35;for(int[] offset:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})c.drawText(renderer,text,x+offset[0],y+offset[1],0xff000000,false);c.drawText(renderer,text,x,y,0xff80ff20,false);}
}
