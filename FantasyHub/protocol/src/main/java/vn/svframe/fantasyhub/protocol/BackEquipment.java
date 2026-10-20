package vn.svframe.fantasyhub.protocol;

import java.lang.reflect.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.*;
import net.minecraft.util.Pair;

/** Functional back slots only; optional APIs are resolved once, never hard dependencies. */
public final class BackEquipment {
  private record Backend(boolean accessories,Method component,Method equipped,Method slot,Method reference,Method stack){}
  private static final List<Backend> BACKENDS=new ArrayList<>();
  private BackEquipment(){}
  public static boolean isBackSlot(String path){
    if(path.equals("back"))return true;
    var p=path.split("/");return p.length==3&&!p[0].equals("cosmetic")&&p[1].equals("back")&&p[2].matches("[0-9]+");
  }
  static void install(){
    register(true,"accessories","io.wispforest.accessories.api.AccessoriesAPI","io.wispforest.accessories.api.Accessory");
    register(false,"trinkets","dev.emi.trinkets.api.TrinketsApi","dev.emi.trinkets.api.Trinket");
  }
  private static void register(boolean accessories,String mod,String apiName,String contractName){
    if(!FabricLoader.getInstance().isModLoaded(mod))return;
    try{
      var api=Class.forName(apiName);var contract=Class.forName(contractName);
      var ref=Class.forName(accessories?"io.wispforest.accessories.api.slot.SlotReference":"dev.emi.trinkets.api.SlotReference");
      var slot=ref.getMethod(accessories?"slotName":"getId");
      var entity=accessories?ref.getMethod("entity"):null;
      var component=Class.forName(accessories?"io.wispforest.accessories.api.AccessoriesCapability":"dev.emi.trinkets.api.TrinketComponent");
      var getter=accessories?component.getMethod("get",LivingEntity.class):api.getMethod("getTrinketComponent",LivingEntity.class);
      Class<?> row=accessories?Class.forName("io.wispforest.accessories.api.slot.SlotEntryReference"):null;
      BACKENDS.add(new Backend(accessories,getter,component.getMethod("getEquipped",Item.class),slot,accessories?row.getMethod("reference"):null,accessories?row.getMethod("stack"):null));
      var adapter=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(self,m,args)->{
        if(m.getDeclaringClass()==Object.class)return switch(m.getName()){case "toString"->"FantasyHub back capes ("+mod+")";case "hashCode"->System.identityHashCode(self);case "equals"->self==args[0];default->null;};
        if(m.getName().equals("canEquip")){
          var wearer=accessories?(LivingEntity)entity.invoke(args[1]):(LivingEntity)args[2];
          if(!isBackSlot((String)slot.invoke(args[1])))return false;
          var existing=equippedAny(wearer);return existing.isEmpty()||existing==args[0];
        }
        if(m.isDefault())return InvocationHandler.invokeDefault(self,m,args==null?new Object[0]:args);
        throw new UnsupportedOperationException("Unrecognized "+mod+" cape API method: "+m);
      });
      var method=api.getMethod(accessories?"registerAccessory":"registerTrinket",Item.class,contract);
      method.invoke(null,Protocol.batmanCape,adapter);method.invoke(null,Protocol.thorCape,adapter);method.invoke(null,Protocol.levitationCloak,adapter);
    }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot initialize installed "+mod+" back-slot API",e);}
  }
  public static ItemStack equippedAny(LivingEntity entity){
    for(Item item:new Item[]{Protocol.batmanCape,Protocol.thorCape,Protocol.levitationCloak}){var stack=equipped(entity,item);if(!stack.isEmpty())return stack;}
    return ItemStack.EMPTY;
  }
  public static ItemStack equipped(LivingEntity entity,Item item){
    if(item==null)return ItemStack.EMPTY;
    try{
      for(var b:BACKENDS){
        Object component=b.component.invoke(null,entity);
        if(!b.accessories)component=((Optional<?>)component).orElse(null);
        if(component==null)continue;
        for(Object row:(List<?>)b.equipped.invoke(component,item)){
          Object ref=b.accessories?b.reference.invoke(row):((Pair<?,?>)row).getLeft();
          if(isBackSlot((String)b.slot.invoke(ref)))return b.accessories?(ItemStack)b.stack.invoke(row):(ItemStack)((Pair<?,?>)row).getRight();
        }
      }
      return ItemStack.EMPTY;
    }catch(ReflectiveOperationException e){throw new IllegalStateException("Installed back-slot API failed",e);}
  }
}
