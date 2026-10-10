package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Pixel aligned panel primitives with consistent scales and clipped labels. */
public final class FantasyPanel {
    private FantasyPanel(){}
    static void frame(DrawContext c,int x,int y,int w,int h){
        c.fill(x,y,x+w,y+h,0xf21a1414);c.fill(x+2,y+2,x+w-2,y+h-2,0xe52b2020);
        c.fill(x+4,y+4,x+w-4,y+h-4,0xef171516);
        c.fill(x+2,y,x+w-2,y+1,0xffb99456);c.fill(x+2,y+h-1,x+w-2,y+h,0xff8e673d);
        c.fill(x,y+2,x+1,y+h-2,0xffb99456);c.fill(x+w-1,y+2,x+w,y+h-2,0xff8e673d);
        for(int cx:new int[]{x+2,x+w-4})for(int cy:new int[]{y+2,y+h-4})c.fill(cx,cy,cx+2,cy+2,0xffe6c57e);
    }
    static void bar(DrawContext c,int x,int y,int w,int h,double value,double max,int color,String name){
        c.fill(x,y,x+w,y+h,0xff09090b);c.fill(x+1,y+1,x+w-1,y+h-1,0xff31272b);
        int fill=(int)Math.round((w-2)*Math.max(0,Math.min(1,max<=0?0:value/max)));
        c.fill(x+1,y+1,x+1+fill,y+h-1,color);c.fill(x+1,y+1,x+1+fill,y+2,0xffd8bfa9);
        var text=MinecraftClient.getInstance().textRenderer;
        c.drawText(text,Text.translatable("fantasyhub.resource."+name),x+4,y+2,0xfff8ede0,true);
        String amount=number(value)+" / "+number(max);
        c.drawText(text,amount,x+w-text.getWidth(amount)-4,y+2,0xfff8ede0,true);
    }
    private static String number(double value){return Math.abs(value-Math.rint(value))<.005?String.format(java.util.Locale.ROOT,"%.0f",value):String.format(java.util.Locale.ROOT,"%.1f",value);}
}
