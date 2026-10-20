package vn.svframe.fantasyhub.core.hero;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UnbreakableComponent;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.ItemCodec;
/** Generated, rebuilt and previously issued hero RPG gear retains all instance data and never wears out. */
final class HeroGearIntegrity {
 static void install(){
  SVFrameItems.generator().registerMechanic((stack,definition,instance,phase)->protect(stack));
  ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->{
   var inv=handler.player.getInventory();for(int i=0;i<inv.size();i++)protect(inv.getStack(i));
  });
 }
 private static void protect(ItemStack stack){
  var component=stack.get(DataComponentTypes.CUSTOM_DATA);if(component==null)return;
  String id=component.copyNbt().getString("SVFRAMEITEMS_ITEM_ID").toUpperCase(java.util.Locale.ROOT);
  if(id.startsWith("HERO_")&&ItemCodec.isSVFrameItem(stack))stack.set(DataComponentTypes.UNBREAKABLE,new UnbreakableComponent(true));
 }
}
