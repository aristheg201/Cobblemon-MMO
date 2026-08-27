package vn.svframe.svframeitems.api;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
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
    public static Optional<String> metadata(ItemStack stack,String key){return ItemCodec.metadata(stack,key);}
    public static boolean setMetadata(ItemStack stack,String key,String value){return ItemCodec.setMetadata(stack,key,value);}
    public static ItemStack generate(String itemId,int level){return SVFrameItems.generator().generate(itemId,level);}
    public static ItemStack generate(String itemId,int level,long seed){return SVFrameItems.generator().generate(itemId,new ItemGenerator.GenerationContext(level,seed));}
    public static UpgradeService.Result upgrade(ItemStack stack){return SVFrameItems.upgrades().attempt(stack);}
    public static UpgradeService.Result upgrade(ServerPlayerEntity player,ItemStack stack){return SVFrameItems.upgrades().attempt(player,stack);}
    public static SocketService.InsertResult socket(ItemStack target,ItemStack gem){return SVFrameItems.sockets().insert(target,gem);}
    public static SocketService.UnsocketResult unsocket(ItemStack target,int index){return SVFrameItems.sockets().unsocket(target,index);}
    public static List<ItemStack> rollLoot(String tableId,int level){return SVFrameItems.loot().roll(tableId,level);}
    public static List<ItemStack> rollLoot(String tableId,LootService.Context context){return SVFrameItems.loot().roll(tableId,context);}
    public static boolean canCraft(net.minecraft.inventory.Inventory inventory,String recipeId){return SVFrameItems.recipes().canCraft(inventory,recipeId);}
    public static RecipeService.Result craft(ServerPlayerEntity player,String recipeId){return SVFrameItems.recipes().craft(player,recipeId);}
    public static boolean canEquip(ItemStack stack,NativeStatEngine.EquipmentSlot slot){Optional<ItemInstance> item=identify(stack);if(item.isEmpty())return false;ItemDefinition definition=registry().item(item.get().definitionId());if(definition==null)return false;ItemType type=registry().type(definition.typeId());return type!=null&&type.canEquip(slot);}
    public static List<ItemStat> effectiveStats(ItemStack stack){return identify(stack).map(item->item.effectiveStats(SVFrameItems.upgrades().statMultiplier(item),SVFrameItems.upgrades()::statMultiplier)).orElse(List.of());}
    public static void refreshEquipment(ServerPlayerEntity player){SVFrameItems.equipment().refresh(player);}
    public static void rebindEquipment(ServerPlayerEntity player){SVFrameItems.equipment().refresh(player,true);}
    public static AutoCloseable registerEquipmentProvider(EquipmentProvider provider){return EquipmentProviderRegistry.register(provider);}
    public static AutoCloseable registerItemMechanic(ItemGenerator.Mechanic mechanic){return SVFrameItems.generator().registerMechanic(mechanic);}
    public static AutoCloseable registerUpgradeCostProvider(UpgradeService.CostProvider provider){return SVFrameItems.upgrades().registerCostProvider(provider);}
    public static AutoCloseable registerLootCondition(String id,LootService.Condition condition){return SVFrameItems.loot().registerCondition(id,condition);}
    public static AutoCloseable registerLootReward(String id,LootService.Reward reward){return SVFrameItems.loot().registerReward(id,reward);}
    public static void register(ItemType value){registry().registerExternal(value);} public static void register(ItemRarity value){registry().registerExternal(value);} public static void register(ItemDefinition value){registry().registerExternal(value);} public static void register(ItemSetDefinition value){registry().registerExternal(value);} public static void register(UpgradeTemplate value){registry().registerExternal(value);} public static void register(RecipeDefinition value){registry().registerExternal(value);} public static void register(LootTableDefinition value){registry().registerExternal(value);}
    public static void validateRegistry(){registry().validateSnapshot();}
    public static void validateRuntimeRegistry(){RuntimeDefinitionValidator.validate(registry(),SVFrameItems.upgrades(),SVFrameItems.loot());}
    public static boolean reload(){return SVFrameItems.reload();}
}
