package vn.svframe.svframeitems.runtime;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.ArrayList;

/** Restore original slots first; preserve occupied vanilla slots and pending overflow. */
public final class RetainedInventory {
    private RetainedInventory() {}
    public static void restore(ServerPlayerEntity player){
        if(!player.isAlive())return;
        var retained=((RetainedItems)player).svframe$retained();if(retained.isEmpty())return;
        for(var entry:new ArrayList<>(retained.entrySet())){
            int slot=entry.getKey();
            if(slot<0||!player.getInventory().getStack(slot).isEmpty()){
                slot=-1;for(int i=0;i<36;i++)if(player.getInventory().getStack(i).isEmpty()){slot=i;break;}
            }
            if(slot>=0){player.getInventory().setStack(slot,entry.getValue());retained.remove(entry.getKey());}
        }
    }
}
