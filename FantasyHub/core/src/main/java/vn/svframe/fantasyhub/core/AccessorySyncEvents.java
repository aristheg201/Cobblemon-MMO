package vn.svframe.fantasyhub.core;
import java.lang.reflect.*;import net.fabricmc.loader.api.FabricLoader;import net.fabricmc.fabric.api.event.Event;import net.minecraft.server.network.ServerPlayerEntity;import vn.svframe.fantasyhub.core.sync.Domain;
/** Optional APIs stay outside mandatory signatures. Listeners retain no player/session objects. */
final class AccessorySyncEvents {
 private static boolean installed;
 static void install(){if(installed)return;installed=true;
  register("accessories","io.wispforest.accessories.api.events.AccessoryChangeCallback",2,"io.wispforest.accessories.api.slot.SlotReference");
  register("accessories","io.wispforest.accessories.api.events.ContainersChangeCallback",0,null);
  register("trinkets","dev.emi.trinkets.api.event.TrinketEquipCallback",2,null);
  register("trinkets","dev.emi.trinkets.api.event.TrinketUnequipCallback",2,null);
 }
 @SuppressWarnings("unchecked") private static void register(String mod,String contractName,int argument,String referenceName){
  if(!FabricLoader.getInstance().isModLoaded(mod))return;
  try{
   Class<?> contract=Class.forName(contractName);Method entity=referenceName==null?null:Class.forName(referenceName).getMethod("entity");
   Object listener=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(self,method,args)->{
    if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"FantasyHub equipment invalidation";case "hashCode"->System.identityHashCode(self);case "equals"->self==args[0];default->null;};
    if(contractName.endsWith("ContainersChangeCallback")&&!vn.svframe.fantasyhub.core.sync.EquipmentEventPolicy.containerChanged((java.util.Map<?,?>)args[2]))return null;
    if(contractName.endsWith("AccessoryChangeCallback")&&!vn.svframe.fantasyhub.core.sync.EquipmentEventPolicy.stackChanged(net.minecraft.item.ItemStack.areEqual((net.minecraft.item.ItemStack)args[0],(net.minecraft.item.ItemStack)args[1])))return null;
    if(contractName.startsWith("dev.emi.")&&((net.minecraft.item.ItemStack)args[0]).isEmpty())return null;
    Object wearer=entity==null?args[argument]:entity.invoke(args[argument]);
    if(wearer instanceof ServerPlayerEntity p)SyncSignals.changed(p.getUuid(),Domain.EQUIPMENT,Domain.COMBAT,Domain.VITALS,Domain.EXTENSIONS,Domain.GUI);
    return null;
   });
   ((Event<Object>)contract.getField("EVENT").get(null)).register(listener);
  }catch(ReflectiveOperationException e){org.slf4j.LoggerFactory.getLogger("FantasyHub/Sync").warn("Equipment event unavailable: {}",contractName,e);}
 }
}
