package io.lumine.mythic.lib.gui;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import net.minecraft.entity.player.PlayerEntity; import net.minecraft.inventory.Inventory; import net.minecraft.screen.*; import net.minecraft.screen.slot.SlotActionType; import net.minecraft.server.network.ServerPlayerEntity; import net.minecraft.text.Text;
import java.util.function.Consumer;
public abstract class PluginInventory {
    protected final ServerPlayerEntity player; protected final MMOPlayerData playerData; protected final Navigator navigator; public int page;
    private Consumer<Inventory> backgroundRunnable; private long backgroundRunnablePeriod;
    public PluginInventory(MMOPlayerData data){this(new Navigator(data));}
    public PluginInventory(ServerPlayerEntity player){this(new Navigator(player));}
    public PluginInventory(Navigator navigator){this.navigator=navigator;this.playerData=navigator.getMMOPlayerData();this.player=playerData.getPlayer();}
    public MMOPlayerData getMMOPlayerData(){return playerData;} public ServerPlayerEntity getPlayer(){return player;} public Navigator getNavigator(){return navigator;}
    public long getCloseTimeOut(){return 0L;} public void registerRepeatingTask(Consumer<Inventory> task,long period){backgroundRunnable=task;backgroundRunnablePeriod=period;}
    public Consumer<Inventory> getBackgroundRunnable(){return backgroundRunnable;} public long getBackgroundRunnablePeriod(){return backgroundRunnablePeriod;}
    public void open(){navigator.pushOpen(this);}
    void openDirect(){
        Inventory inv=getInventory();int rows=Math.max(1,Math.min(6,(inv.size()+8)/9));onOpen();
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId,pinv,p)->new GenericContainerScreenHandler(type(rows),syncId,pinv,inv,rows){
            @Override public void onSlotClick(int slotIndex,int button,SlotActionType actionType,PlayerEntity clicking){
                if(slotIndex>=0&&slotIndex<inv.size()){PluginInventory.this.onClick(new Click(slotIndex,button,actionType));return;}
                super.onSlotClick(slotIndex,button,actionType,clicking);
            }
            @Override public void onClosed(PlayerEntity p){super.onClosed(p);PluginInventory.this.onClose();}
        },Text.literal(getTitle())));
    }
    public String getTitle(){return getClass().getSimpleName();}
    private static ScreenHandlerType<GenericContainerScreenHandler> type(int rows){return switch(rows){case 1->ScreenHandlerType.GENERIC_9X1;case 2->ScreenHandlerType.GENERIC_9X2;case 3->ScreenHandlerType.GENERIC_9X3;case 4->ScreenHandlerType.GENERIC_9X4;case 5->ScreenHandlerType.GENERIC_9X5;default->ScreenHandlerType.GENERIC_9X6;};}
    public abstract Inventory getInventory(); public abstract void onClick(Click click); public void onDrag(){} public void onClose(){} public void onOpen(){}
    public record Click(int slot,int button,SlotActionType actionType){}
}
