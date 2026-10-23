package vn.svframe.fantasyhub.core;

import com.google.gson.Gson;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.api.SVFrameItemsApi;
import vn.svframe.svframeitems.model.UpgradeTemplate;
import vn.svframe.svframeitems.item.UpgradeService;
import vn.svframe.svframemmo.SVFrameMMO;

/** HUD adapter for the existing SVFrameItems upgrade engine; never accepts client prices or levels. */
public final class ItemUpgradeRuntime {
 private static final UpgradeQuotes QUOTES=new UpgradeQuotes();
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("FantasyHub/Upgrade");
 private static final Set<String> ROLLED=Set.of("SUCCESS","FAILED","DESTROYED");
 private static final Identifier CATALYST=Identifier.of("fantasyhub","rpg_upgrade_catalyst");
 private static final String RECEIPT="fantasyhub:last-item-upgrade";
 static void keepAlive(ServerPlayerEntity p){QUOTES.keepAlive(p.getUuid(),System.currentTimeMillis());}
 static void install(){net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h,s)->QUOTES.clear(h.player.getUuid()));}
 private static String encoded(ServerPlayerEntity p,ItemStack stack){return stack.isEmpty()?"{}":stack.encode(p.getRegistryManager()).toString();}
 private static UpgradeTemplate template(ItemStack stack){return SVFrameItemsApi.identify(stack).map(i->SVFrameItems.registry().item(i.definitionId())).map(d->SVFrameItems.upgrades().template(d)).orElse(null);}
 private static Path journal(ServerPlayerEntity p){return p.getServer().getSavePath(WorldSavePath.ROOT).resolve("fantasyhub-upgrades").resolve(p.getUuid()+".json");}
 private static boolean pendingRecovery(ServerPlayerEntity p){
  Path file=journal(p);if(!Files.exists(file))return false;
  try{String id=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("transaction").getAsString();if(id.equals(SVFrameMMO.playerData().get(p).getPreference(RECEIPT,""))){Files.delete(file);return false;}}
  catch(Exception failure){LOG.error("Upgrade recovery record retained for {}",p.getUuid(),failure);}
  return true;
 }
 private static int catalystSlot(ServerPlayerEntity p){for(int i=0;i<p.getInventory().size();i++)if(p.getInventory().getStack(i).isOf(Registries.ITEM.get(CATALYST)))return i;return -1;}
 public static Map<String,Object> view(ServerPlayerEntity p){
  var out=new LinkedHashMap<String,Object>();var rows=new ArrayList<Map<String,Object>>();int catalysts=0;
  boolean recovery=pendingRecovery(p);
  for(int slot=0;slot<p.getInventory().size();slot++){
   var st=p.getInventory().getStack(slot);if(st.isOf(Registries.ITEM.get(CATALYST)))catalysts+=st.getCount();
   var identified=SVFrameItemsApi.identify(st);if(identified.isEmpty())continue;var item=identified.get();var t=template(st);var row=new LinkedHashMap<String,Object>();
   row.put("slot",slot);row.put("item",encoded(p,st));row.put("definition",item.definitionId());row.put("level",item.upgradeLevel());row.put("max",t==null?item.upgradeLevel():t.maxLevel());
   String blocked=recovery?"recovery":st.getCount()>1&&p.getInventory().getEmptySlot()<0?"inventory_full":t==null?"not_upgradable":item.upgradeLevel()>=t.maxLevel()?"max_level":"";
   row.put("blocked",blocked);row.put("chance",t==null?0:t.chanceForNextLevel(item.upgradeLevel()));row.put("destroyOnFail",t!=null&&t.destroyOnFail());
   var costs=new ArrayList<Map<String,Object>>();if(t!=null)for(var cost:t.costs()){
    var c=new LinkedHashMap<String,Object>();c.put("provider",cost.provider());c.put("id",cost.id());c.put("amount",cost.amountForNextLevel(item.upgradeLevel()));
    if(cost.provider().equals("minecraft_item")&&Registries.ITEM.containsId(Identifier.of(cost.id())))c.put("item",encoded(p,new ItemStack(Registries.ITEM.get(Identifier.of(cost.id())))));
    costs.add(c);
   }
   row.put("costs",costs);row.put("quote",blocked.isEmpty()?QUOTES.issue(p.getUuid(),slot,encoded(p,st),t.toString(),System.currentTimeMillis()):"");rows.add(row);
  }
  out.put("catalysts",catalysts);out.put("recovery",recovery);out.put("items",rows);return out;
 }
 public static boolean request(ServerPlayerEntity p,String token){
  var data=SVFrameMMO.playerData().get(p);var q=QUOTES.take(p.getUuid(),token,System.currentTimeMillis());
  if(q==null)return reject(p,"changed");
  if(ClassProgression.busy(data)||ClassSwitchService.busy(data)||pendingRecovery(p))return reject(p,"recovery");
  if(!p.currentScreenHandler.equals(p.playerScreenHandler))return reject(p,"close_container");
  var target=p.getInventory().getStack(q.slot());var t=template(target);int catalyst=catalystSlot(p);
  if(!encoded(p,target).equals(q.item())||t==null||!t.toString().equals(q.rules()))return reject(p,"changed");
  if(catalyst<0)return reject(p,"missing_catalyst");
  Path intent=journal(p);boolean attempted=false,prepared=false;
  try{
   var inventory=new ArrayList<String>();for(int i=0;i<p.getInventory().size();i++)inventory.add(encoded(p,p.getInventory().getStack(i)));
   var record=Map.of("transaction",token,"slot",q.slot(),"inventoryBefore",inventory,"rules",q.rules(),"createdAt",System.currentTimeMillis());
   Files.createDirectories(intent.getParent());Path tmp=intent.resolveSibling(p.getUuid()+".tmp");byte[] bytes=new Gson().toJson(record).getBytes(StandardCharsets.UTF_8);
   try(var channel=FileChannel.open(tmp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
   Files.move(tmp,intent,StandardCopyOption.ATOMIC_MOVE);prepared=true;
   int free=p.getInventory().getEmptySlot();if(target.getCount()>1&&free<0){Files.delete(intent);return reject(p,"inventory_full");}
   var unit=target.copy();unit.setCount(1);
   if(target.getCount()>1){var old=SVFrameItemsApi.identify(unit).orElseThrow();unit=SVFrameItems.generator().rebuild(unit,new vn.svframe.svframeitems.model.ItemInstance(UUID.randomUUID(),old.definitionId(),old.typeId(),old.rarityId(),old.itemLevel(),old.upgradeLevel(),old.definitionRevision(),old.seed(),old.stateRevision()+1,old.stats(),old.sockets(),old.metadata()));}
   var catalystBefore=p.getInventory().getStack(catalyst).copy();attempted=true;p.getInventory().getStack(catalyst).decrement(1);var result=SVFrameItemsApi.upgrade(p,unit);String status=result.status().name();
   if(!ROLLED.contains(status)){p.getInventory().setStack(catalyst,catalystBefore);Files.delete(intent);return reject(p,status.toLowerCase(Locale.ROOT));}
   if(target.getCount()==1)p.getInventory().setStack(q.slot(),result.item());
   else if(!status.equals("FAILED")){target.decrement(1);if(!result.item().isEmpty())p.getInventory().setStack(free,result.item());}
   p.getInventory().markDirty();
   ((vn.svframe.fantasyhub.protocol.mixin.PlayerSaveAccess)p.getServer().getPlayerManager()).svframe$savePlayer(p);
   data.setPreference(RECEIPT,token);SVFrameMMO.playerData().savePlayerDurably(data).join();
   try{Files.delete(intent);}catch(java.io.IOException cleanup){LOG.error("Upgrade {} committed for {}; retain recovery record for administrator",token,p.getUuid(),cleanup);}
   SVFrameItemsApi.refreshEquipment(p);p.currentScreenHandler.sendContentUpdates();
   p.sendMessage(Text.translatable("fantasyhub.item_upgrade.result."+status.toLowerCase(Locale.ROOT),result.newLevel()),false);return true;
  }catch(Exception failure){if(prepared&&!attempted)try{Files.deleteIfExists(intent);}catch(java.io.IOException cleanup){failure.addSuppressed(cleanup);}if(attempted)ClassProgression.quarantine(data);LOG.error("Item upgrade {} failed for {}; attempted={}; preserved recovery journal",token,p.getUuid(),attempted,failure);return reject(p,attempted?"recovery":"storage");}
 }
 private static boolean reject(ServerPlayerEntity p,String reason){p.sendMessage(Text.translatable("fantasyhub.item_upgrade.error."+reason),false);return false;}
}
