package io.lumine.mythic.lib.gui;
import io.lumine.mythic.lib.api.player.MMOPlayerData; import net.minecraft.inventory.Inventory; import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
public class Navigator {
    private final Deque<PluginInventory> openedInventories=new ArrayDeque<>(); private final MMOPlayerData playerData; private PluginInventory lastInvOpened; private Inventory lastOpened; private boolean canClose=true,closed,onHold; public boolean recycle;
    public Navigator(ServerPlayerEntity player){this(MMOPlayerData.has(player)?MMOPlayerData.get(player):MMOPlayerData.setup(player));}
    public Navigator(MMOPlayerData data){this.playerData=Objects.requireNonNull(data);}
    public MMOPlayerData getMMOPlayerData(){return playerData;} public void blockClosing(){canClose=false;} public void unblockClosing(){canClose=true;}
    public PluginInventory push(PluginInventory inv){openedInventories.push(inv);lastInvOpened=inv;return inv;}
    public PluginInventory pushOpen(PluginInventory inv){if(openedInventories.peek()!=inv)push(inv);lastOpened=inv.getInventory();inv.openDirect();closed=false;return inv;}
    public boolean isClosed(){return closed;} public Inventory getLastBukkitOpened(){return lastOpened;} public Inventory getLastOpened(){return lastOpened;} public PluginInventory peek(){return openedInventories.peek();}
    public PluginInventory openLast(){PluginInventory inv=openedInventories.peek();if(inv!=null){lastOpened=inv.getInventory();inv.openDirect();}return inv;}
    public PluginInventory popOpen(){if(!openedInventories.isEmpty())openedInventories.pop();PluginInventory next=openedInventories.peek();if(next==null){if(canClose)close();return null;}next.openDirect();return next;}
    public void close(){if(!canClose)return;closed=true;openedInventories.clear();playerData.getPlayer().closeHandledScreen();}
    public void hold(){onHold=true;} public void release(){onHold=false;} public boolean isOnHold(){return onHold;}
}
