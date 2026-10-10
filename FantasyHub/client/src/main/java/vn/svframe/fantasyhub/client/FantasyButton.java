package vn.svframe.fantasyhub.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Pixel panel buttons retain vanilla keyboard navigation, narration and tooltip behavior. */
final class FantasyButton extends ButtonWidget {
    private FantasyButton(int x,int y,int width,int height,Text text,PressAction press){super(x,y,width,height,text,press,DEFAULT_NARRATION_SUPPLIER);}
    public static Builder builder(Text text,PressAction press){return new Builder(text,press);}
    static final class Builder extends ButtonWidget.Builder {
        private final Text text;private final PressAction press;private int x,y,w=150,h=20;
        Builder(Text text,PressAction press){super(text,press);this.text=text;this.press=press;}
        @Override public Builder dimensions(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        @Override public FantasyButton build(){return new FantasyButton(x,y,w,h,text,press);}
    }
    @Override protected void renderWidget(DrawContext context,int mouseX,int mouseY,float delta){
        FantasyPanel.frame(context,getX(),getY(),getWidth(),getHeight());
        boolean highlighted=active&&(isHovered()||isFocused());
        if(highlighted){context.fill(getX()+4,getY()+4,getX()+getWidth()-4,getY()+getHeight()-4,0xff713139);context.fill(getX()+3,getY()+getHeight()-3,getX()+getWidth()-3,getY()+getHeight()-2,0xffe9c86c);}
        var renderer=MinecraftClient.getInstance().textRenderer;String label=renderer.trimToWidth(getMessage().getString(),Math.max(0,getWidth()-10));
        context.drawCenteredTextWithShadow(renderer,Text.literal(label),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?(highlighted?0xfff6e5b8:0xffdec591):0xff776854);
    }
}
