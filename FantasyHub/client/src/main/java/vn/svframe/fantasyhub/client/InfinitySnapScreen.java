package vn.svframe.fantasyhub.client;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
/** Cinematic native carousel presentation. The server controls every reel item and the result. */
final class InfinitySnapScreen extends GenericContainerScreen {
 InfinitySnapScreen(GenericContainerScreenHandler handler,PlayerInventory inventory,Text title){super(handler,inventory,title);}
 @Override protected void drawBackground(DrawContext c,float delta,int mouseX,int mouseY){
  c.fillGradient(x-5,y-5,x+backgroundWidth+5,y+backgroundHeight+5,0xf51a082e,0xf508101d);
  c.fill(x-5,y-5,x+backgroundWidth+5,y-3,0xffd99bff);c.fill(x-5,y+backgroundHeight+3,x+backgroundWidth+5,y+backgroundHeight+5,0xff8056b5);
  c.fill(x-5,y-3,x-3,y+backgroundHeight+3,0xff9761cb);c.fill(x+backgroundWidth+3,y-3,x+backgroundWidth+5,y+backgroundHeight+3,0xff9761cb);
  c.fillGradient(x,y,x+backgroundWidth,y+16,0xff4b205d,0xff20132f);
  for(int row=0;row<3;row++)for(int col=0;col<9;col++){
   int sx=x+7+col*18,sy=y+17+row*18;
   c.fill(sx,sy,sx+18,sy+18,col==4?0xff997a35:0xff352841);
   c.fill(sx+1,sy+1,sx+17,sy+17,row==1?0xff1e142c:0xff15101e);
  }
  c.fill(x+78,y+16,x+80,y+72,0xffffd985);c.fill(x+96,y+16,x+98,y+72,0xffffd985);
  for(var slot:handler.slots)if(slot.inventory==client.player.getInventory()){
   int sx=x+slot.x-1,sy=y+slot.y-1;c.fill(sx,sy,sx+18,sy+18,0xff43364e);c.fill(sx+1,sy+1,sx+17,sy+17,0xff14101b);
  }
 }
 @Override protected void drawForeground(DrawContext c,int mouseX,int mouseY){
  c.drawCenteredTextWithShadow(textRenderer,Text.literal("✦ INFINITY SNAP ✦"),backgroundWidth/2,5,0xffffd7ff);
  c.drawText(textRenderer,playerInventoryTitle,8,playerInventoryTitleY,0xffbca7d3,false);
 }
}
