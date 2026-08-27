package vn.svframe.svframeitems.api;

import net.minecraft.item.ItemStack;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.*;
import vn.svframe.svframeitems.model.*;
import vn.svframe.svframeitems.registry.SVFrameItemsRegistry;
import vn.svframe.svframeitems.runtime.*;
import java.util.*;

public final class SVFrameItemsApi {
    private SVFrameItemsApi(){}
    public static SVFrameItemsRegistry registry(){return SVFrameItems.registry();}
    public static Optional<ItemInstance> identify(ItemStack stack){return ItemCodec.read(stack);}
    public static ItemStack generate(String itemId,int level){return SVFrameItems.generator().generate(itemId,level);}
    public static ItemStack generate(String itemId,int level,long seed){return SVFrameItems.generator().generate(itemId,new ItemGenerator.GenerationContext(level,seed));}
    public static UpgradeService.Result upgrade(ItemStack stack){return SVFrameItems.upgrades().attempt(stack);}
    public static SocketService.InsertResult socket(ItemStack target,ItemStack gem){return SVFrameItems.sockets().insert(target,gem);}
    public static SocketService.UnsocketResult unsocket(ItemStack target,int index){return SVFrameItems.sockets().unsocket(target,index);}
    public static List<ItemStack> rollLoot(String tableId,int level){return SVFrameItems.loot().roll(tableId,level);}
    public static AutoCloseable registerEquipmentProvider(EquipmentProvider provider){return EquipmentProviderRegistry.register(provider);}
    public static void register(ItemType type){registry().registerExternal(type);} public static void register(ItemRarity rarity){registry().registerExternal(rarity);} public static void register(ItemDefinition item){registry().registerExternal(item);}
    public static boolean reload(){return SVFrameItems.reload();}
}
