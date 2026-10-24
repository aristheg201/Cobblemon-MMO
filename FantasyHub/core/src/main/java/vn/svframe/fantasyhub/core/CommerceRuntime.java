package vn.svframe.fantasyhub.core;

import com.google.gson.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import vn.svframe.svframelib.api.economy.*;
import vn.svframe.svframeitems.SVFrameItems;
import vn.svframe.svframeitems.item.ItemGenerator;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.persistence.PlayerDataSnapshot;

/** Native class entitlements, BEconomy transactions and durable item delivery. No currency balances here. */
public final class CommerceRuntime {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("FantasyCommerce");
 private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("FantasyHub/commerce.json");
 private static final String MAIL="fantasyhub:mail", RECEIPT="fantasyhub:last-paid-switch";
 private static final Set<UUID> BUSY=new HashSet<>();
 private static final Map<UUID,Map<String,Quote>> QUOTES=new HashMap<>();
 private static Config config=new Config();
 private static long revision;
 private static final SyncTimings VIEW_TIMES=new SyncTimings(),TX_TIMES=new SyncTimings(),SAVE_TIMES=new SyncTimings();
 static Map<String,Object> metrics(){return Map.of("view",VIEW_TIMES.view(),"transaction",TX_TIMES.view(),"durableWait",SAVE_TIMES.view(),"previewCache",PREVIEWS.metrics(),"previewPrepared",warmIndex,"previewTotal",warm.size());}
 static void resetMetrics(){VIEW_TIMES.reset();TX_TIMES.reset();SAVE_TIMES.reset();PREVIEWS.resetMetrics();}
 private static final Map<String,String> PREVIEW_DATA=new LinkedHashMap<>();
 private static final Map<String,Map<String,Object>> ROW_TEMPLATES=new LinkedHashMap<>();
 private static vn.svframe.fantasyhub.core.sync.FrozenValues.Shared sharedTemplates;
 private static final Map<String,Boolean> GEAR_VALIDITY=new HashMap<>();
 private record PreviewTask(String key,java.util.function.Supplier<ItemStack> factory){}
 private static List<PreviewTask> warm=List.of();private static int warmIndex;private static long warmRevision=-1;
 static long previewRevision(){return (revision<<32)^SVFrameItems.registry().revision();}
 static Map<String,String> previewTable(){return Map.copyOf(PREVIEW_DATA);}
 static boolean preparePreviews(ServerPlayerEntity p,long nanos,int count){
  if(nanos<=0)return false;
  long rev=previewRevision();if(warmRevision!=rev){PREVIEWS.clear();PREVIEW_DATA.clear();ROW_TEMPLATES.clear();GEAR_VALIDITY.clear();sharedTemplates=null;var jobs=new LinkedHashMap<String,PreviewTask>();
   for(var rewards:config.equipment.values())for(var reward:rewards){String key="reward:"+reward.item+":"+reward.count;jobs.putIfAbsent(key,new PreviewTask(key,()->stack(reward)));}
   for(var offer:config.offers)if(offer.enabled){for(var reward:offer.items){String key="reward:"+reward.item+":"+reward.count;jobs.putIfAbsent(key,new PreviewTask(key,()->stack(reward)));}String key=iconKey(offer.classId,offer.icon);jobs.putIfAbsent(key,new PreviewTask(key,()->icon(offer.classId,offer.icon)));}
   for(var type:SVFrameMMO.classes().getAll())if(config.classTiers.containsKey(type.getId())&&ClassSwitchService.selectable(type.getId())){String key=iconKey(type.getId(),"minecraft:nether_star");jobs.putIfAbsent(key,new PreviewTask(key,()->type.getRawIcon().toItemStack()));}
   warm=List.copyOf(jobs.values());warmIndex=0;warmRevision=rev;
  }
  long start=System.nanoTime();for(int n=0;warmIndex<warm.size()&&n<count&&(n==0||System.nanoTime()-start<nanos);n++){var task=warm.get(warmIndex++);preview(p,task.key,task.factory);}return warmIndex==warm.size();
 }
 private static String iconKey(String cl,String icon){return "icon:"+cl+":"+icon+":"+System.identityHashCode(cl.isBlank()?null:SVFrameMMO.classes().get(cl));}
 private static ItemStack icon(String cl,String icon){return !cl.isBlank()&&SVFrameMMO.classes().get(cl)!=null?SVFrameMMO.classes().get(cl).getRawIcon().toItemStack():new ItemStack(Registries.ITEM.get(Identifier.of(icon)));}
 private static final PreviewCache<String,String> PREVIEWS=new PreviewCache<>(4096);
 private static final Map<UUID,Long> QUOTE_LEASES=new HashMap<>();
 private static final Map<UUID,Map.Entry<String,Integer>> MAIL_COUNTS=new HashMap<>();
 static long revision(){return revision;}
 static void keepAlive(ServerPlayerEntity p){QUOTE_LEASES.put(p.getUuid(),System.currentTimeMillis()+120_000);}
 private static int mailCount(ServerPlayerEntity p){String ref=SVFrameMMO.playerData().get(p).getPreference(MAIL,"[]");var cached=MAIL_COUNTS.get(p.getUuid());if(cached!=null&&cached.getKey().equals(ref))return cached.getValue();int count=mail(p).size();MAIL_COUNTS.put(p.getUuid(),Map.entry(ref,count));return count;}
 public static Map<String,Object> status(ServerPlayerEntity p){var d=SVFrameMMO.playerData().get(p);var service=CurrencyService.get();var out=new LinkedHashMap<String,Object>();for(int m=1;m<=2;m++){var key=CommerceCurrencies.payment(m);boolean ready=service.isAvailable(key);out.put(m==1?"beastAvailable":"hunterAvailable",ready);out.put(m==1?"beastBalance":"hunterBalance",ready?service.balance(p,key).toPlainString():"Unavailable");}out.put("activeClass",d.getClassId());out.put("switchAvailableAt",d.getClassSwitchAvailableAt());out.put("freeChoice",ClassProgression.starter(d));out.put("bypassBeast",config.bypassBeast);try{out.put("mailCount",mailCount(p));out.put("mailAvailable",true);}catch(RuntimeException failed){out.put("mailAvailable",false);}return out;}
 private static String preview(ServerPlayerEntity p,String key,java.util.function.Supplier<ItemStack> create){String encoded=PREVIEWS.get(vn.svframe.svframeitems.SVFrameItems.registry().revision(),key,()->{HudSyncMetrics.previews++;var st=create.get();HudSyncMetrics.encodes++;return st.encode(p.getRegistryManager()).toString();});PREVIEW_DATA.putIfAbsent("preview:"+key,encoded);return "preview:"+key;}
 private static final Set<String> INVALID_GEAR_LOGGED=new HashSet<>();
 public static final class Prices {public BigDecimal beast,hunter;public Prices(){}public Prices(int b,int h){beast=BigDecimal.valueOf(b);hunter=BigDecimal.valueOf(h);}BigDecimal get(int method){CommercePolicy.currency(method);return method==1?beast:hunter;}void validate(){CommercePolicy.positiveWhole(beast);CommercePolicy.positiveWhole(hunter);}}
 public static final class Tier {public Prices classPrice,bundlePrice,equipmentPrice;}
 public static final class Offer {public String id,name,category,description,icon="minecraft:paper",effect="items",classId="";public boolean enabled=true;public Prices price;public List<Reward> items=new ArrayList<>();public int limit=0;public String permission="";}
 public static final class Reward {public String item;public int count=1;public Reward(){}public Reward(String id,int n){item=id;count=n;}}
 public static final class Config {public int version=1;public BigDecimal bypassBeast=BigDecimal.valueOf(500);public Map<String,String> classTiers=new LinkedHashMap<>();public Map<String,Tier> tiers=new LinkedHashMap<>();public Map<String,List<Reward>> equipment=new LinkedHashMap<>();public List<Offer> offers=new ArrayList<>();}
 private record Quote(String offer,String classId,String tier,boolean equipmentOnly,long expires){}
 public static BigDecimal bypassPrice(){return config.bypassBeast;}
 public static void install(){
  CommerceQa.install();ItemUpgradeRuntime.install();
  ServerLifecycleEvents.SERVER_STOPPED.register(server->{PREVIEWS.clear();PREVIEW_DATA.clear();ROW_TEMPLATES.clear();GEAR_VALIDITY.clear();sharedTemplates=null;warm=List.of();warmIndex=0;warmRevision=-1;MAIL_COUNTS.clear();QUOTE_LEASES.clear();QUOTES.clear();});
  vn.svframe.fantasyhub.protocol.CommerceItem.handlers.put("rpg_upgrade_catalyst",(player,kind,hand)->{
   if(!(player instanceof ServerPlayerEntity p)||hand!=net.minecraft.util.Hand.MAIN_HAND)return false;
   var gear=p.getOffHandStack();var result=vn.svframe.svframeitems.api.SVFrameItemsApi.upgrade(p,gear);
   var status=result.status().name();if(!Set.of("SUCCESS","FAILED","DESTROYED").contains(status)){p.sendMessage(Text.literal("Upgrade unavailable: "+status+". Hold RPG gear in offhand."),false);return false;}
   p.setStackInHand(net.minecraft.util.Hand.OFF_HAND,result.item());p.getMainHandStack().decrement(1);((vn.svframe.fantasyhub.protocol.mixin.PlayerSaveAccess)p.getServer().getPlayerManager()).svframe$savePlayer(p);p.sendMessage(Text.literal("RPG upgrade: "+status+" (+"+result.newLevel()+")"),false);return true;
  });
  ServerLifecycleEvents.SERVER_STARTED.register(s->{try{reload();}catch(Exception e){LOG.error("Commerce disabled: invalid configuration",e);config=new Config();}});
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->{QUOTES.remove(h.player.getUuid());});
  ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{QUOTES.remove(h.player.getUuid());QUOTE_LEASES.remove(h.player.getUuid());MAIL_COUNTS.remove(h.player.getUuid());});
  vn.svframe.svframemmo.api.event.PlayerExperienceGainEvent.EVENT.register(e->{try{long expiry=Long.parseLong(e.getData().getPreference("fantasyhub:exp-boost-until","0"));if(e.isMainExperience()&&expiry>System.currentTimeMillis())e.setExperience(e.getExperience()*2);}catch(NumberFormatException ignored){}});
 }
 public static void reload() throws Exception {
  if(!Files.exists(FILE)){Files.createDirectories(FILE.getParent());try(var in=CommerceRuntime.class.getResourceAsStream("/default/commerce.json")){if(in==null)throw new IllegalStateException("Missing commerce catalog");Files.copy(in,FILE);}}
  var catalog=JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();boolean migrated=CommerceCatalogMigration.upgrade(catalog);Config next=JSON.fromJson(catalog,Config.class);if(next==null)throw new IllegalArgumentException("Empty catalog");CommercePolicy.positiveWhole(next.bypassBeast);
  next.tiers.forEach((k,v)->{v.classPrice.validate();v.bundlePrice.validate();v.equipmentPrice.validate();});
  for(var entry:next.classTiers.entrySet())if(SVFrameMMO.classes().get(entry.getKey())==null||!next.tiers.containsKey(entry.getValue()))throw new IllegalArgumentException("Unknown class/tier "+entry);
  for(var type:SVFrameMMO.classes().getAll())if(ClassSwitchService.selectable(type.getId())&&!next.classTiers.containsKey(type.getId()))LOG.error("Class {} has no explicit tier: purchase disabled",type.getId());
  Set<String> ids=new HashSet<>();for(var o:next.offers)try{if(o.id==null||!o.id.matches("[a-z0-9_-]{1,48}")||!ids.add(o.id))throw new IllegalArgumentException("Invalid/duplicate offer ID");o.price.validate();if("items".equals(o.category))CommercePolicy.validateItemPrices(o.price.beast,o.price.hunter);if(!Set.of("items","exp","title").contains(o.effect))throw new IllegalArgumentException("Unsupported reward "+o.effect);if("items".equals(o.effect)&&o.items.isEmpty())throw new IllegalArgumentException("Missing rewards");for(var reward:o.items)validateReward(reward);validateReward(new Reward(o.icon,1));if(o.limit<0)throw new IllegalArgumentException("Negative limit");}catch(RuntimeException bad){o.enabled=false;LOG.error("Rejected shop offer {}: {}",o.id,bad.getMessage());}
  if(migrated){var backup=FILE.resolveSibling("commerce.json.pre-v2.bak");if(!Files.exists(backup))Files.copy(FILE,backup);var temporary=FILE.resolveSibling("commerce.json.migration.tmp");Files.writeString(temporary,JSON.toJson(catalog)+"\n");Files.move(temporary,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);LOG.info("Upgraded shipped Rider bundles with their authored belts and weapons; previous catalog saved to {}",backup);}
  config=next;revision++;GEAR_VALIDITY.clear();ROW_TEMPLATES.clear();sharedTemplates=null;PREVIEWS.clear();PREVIEW_DATA.clear();ROW_TEMPLATES.clear();sharedTemplates=null;warm=List.of();warmIndex=0;warmRevision=-1;QUOTES.clear();QUOTE_LEASES.clear();INVALID_GEAR_LOGGED.clear();for(String id:config.equipment.keySet())gearValid(id);
 }
 private static void validateReward(Reward r){if(r.count<1||r.count>64)throw new IllegalArgumentException("Invalid reward quantity");if(r.item.startsWith("rpg:")){if(SVFrameItems.registry().item(r.item.substring(4))==null)throw new IllegalArgumentException("Missing RPG item "+r.item);}else if(!Registries.ITEM.containsId(Identifier.of(r.item)))throw new IllegalArgumentException("Missing item "+r.item);}
 private static ItemStack stack(Reward r){validateReward(r);var st=r.item.startsWith("rpg:")?SVFrameItems.generator().generate(r.item.substring(4),new ItemGenerator.GenerationContext(1,0)):new ItemStack(Registries.ITEM.get(Identifier.of(r.item)));st.setCount(r.count);return st;}
 private static boolean gearValid(String id){if(GEAR_VALIDITY.containsKey(id))return GEAR_VALIDITY.get(id);var list=config.equipment.get(id);if(list==null||list.size()<4)return false;try{for(var r:list)validateReward(r);GEAR_VALIDITY.put(id,true);return true;}catch(RuntimeException e){if(INVALID_GEAR_LOGGED.add(id))LOG.error("Disabled bundle {}: {}",id,e.getMessage());return false;}}
 public static Map<String,Object> view(ServerPlayerEntity p){return view(p,false);}
 static Map<String,Object> capture(ServerPlayerEntity p){return view(p,true);}
 private static Map<String,Object> view(ServerPlayerEntity p,boolean compact){
  long started=System.nanoTime();HudSyncMetrics.shops++;var d=SVFrameMMO.playerData().get(p);var out=compact?new LinkedHashMap<String,Object>():new LinkedHashMap<String,Object>(status(p));keepAlive(p);
  out.put("switchRemainingSeconds",Math.max(0,(d.getClassSwitchAvailableAt()-System.currentTimeMillis()+999)/1000));var rows=new ArrayList<Map<String,Object>>();var quotes=QUOTES.computeIfAbsent(p.getUuid(),k->new HashMap<>());long lease=QUOTE_LEASES.getOrDefault(p.getUuid(),0L);quotes.entrySet().removeIf(e->Math.max(e.getValue().expires,lease)<System.currentTimeMillis());
  var quoteIndex=new HashMap<String,String>();quotes.forEach((token,q)->quoteIndex.put(q.offer+":"+q.equipmentOnly,token));
  for(var type:SVFrameMMO.classes().getAll()){
   String id=type.getId(),tierId=config.classTiers.get(id);if(tierId==null||!ClassSwitchService.selectable(id))continue;var tier=config.tiers.get(tierId);boolean owned=ClassProgression.owns(d,id)||d.getClassId().equals(id);
   var classRow=row(p,quotes,quoteIndex,"class:"+id,id,tierId,false,type.getName(),"classes","",tier.classPrice,List.of(),"minecraft:nether_star",compact);classRow.put("owned",owned);classRow.put("active",d.getClassId().equals(id));rows.add(classRow);
   if(gearValid(id))rows.add(row(p,quotes,quoteIndex,"bundle:"+id,id,tierId,owned,type.getName()+(owned?" — Equipment":" — Class + Equipment"),"bundles",owned?"Equipment only; ownership is already yours":"Class entitlement + listed equipment; does not activate class",owned?tier.equipmentPrice:tier.bundlePrice,config.equipment.get(id),"minecraft:diamond_chestplate",compact));
  }
  for(var o:config.offers)if(o.enabled&&(o.permission.isBlank()||SVFrameMMO.permissions().has(p,o.permission))){var row=row(p,quotes,quoteIndex,o.id,o.classId,"",false,o.name,o.category,o.description,o.price,o.items,o.icon,compact);int bought=d.getClaimCount("fantasyhub:purchases:"+o.id);row.put("limit",o.limit);row.put("bought",bought);row.put("available",o.limit==0||bought<o.limit);rows.add(row);}
  out.put("offers",rows);if(compact){if(sharedTemplates==null)sharedTemplates=vn.svframe.fantasyhub.core.sync.FrozenValues.share(ROW_TEMPLATES);out.put("_templates",sharedTemplates);}VIEW_TIMES.add(System.nanoTime()-started);return out;
 }
 private static Map<String,Object> row(ServerPlayerEntity p,Map<String,Quote> quotes,Map<String,String> quoteIndex,String offer,String cl,String tier,boolean gear,String name,String category,String desc,Prices price,List<Reward> rewards,String icon,boolean compact){
  String token=quoteIndex.computeIfAbsent(offer+":"+gear,k->{String nonce=UUID.randomUUID().toString();quotes.put(nonce,new Quote(offer,cl,tier,gear,System.currentTimeMillis()+120_000));return nonce;});
  String templateId=offer+":"+gear;var base=ROW_TEMPLATES.get(templateId);if(base!=null){var cached=new LinkedHashMap<String,Object>();if(compact){cached.put("_template",templateId);cached.put("product",offer);cached.put("id",token);}else{cached.putAll(base);cached.put("id",token);}return cached;}
  var out=new LinkedHashMap<String,Object>();out.put("id",token);out.put("product",offer);out.put("name",name);out.put("category",category);out.put("description",desc);out.put("beast",price.beast.toPlainString());out.put("hunter",price.hunter.toPlainString());out.put("available",true);out.put("limit",0);out.put("bought",0);out.put("classId",cl);out.put("equipmentOnly",gear);
  var items=new ArrayList<String>();for(var reward:rewards)items.add(preview(p,"reward:"+reward.item+":"+reward.count,()->stack(reward)));out.put("items",items);
  String encoded;if(!rewards.isEmpty())encoded=items.getFirst();else encoded=preview(p,iconKey(cl,icon),()->icon(cl,icon));out.put("icon",encoded);var template=new LinkedHashMap<>(out);template.remove("id");ROW_TEMPLATES.put(templateId,Collections.unmodifiableMap(template));sharedTemplates=null;if(compact)return new LinkedHashMap<>(Map.of("_template",templateId,"product",offer,"id",token));return out;
 }
 public static boolean purchase(ServerPlayerEntity p,String token,int method){
  try{
   CommercePolicy.currency(method);var q=QUOTES.getOrDefault(p.getUuid(),Map.of()).get(token);if(q==null||Math.max(q.expires,QUOTE_LEASES.getOrDefault(p.getUuid(),0L))<System.currentTimeMillis())return reject(p,"expired");
   var d=SVFrameMMO.playerData().get(p);if(d.getClaimCount("fantasyhub:receipt:"+token)>0)return reject(p,"duplicate");
   Prices price;List<Reward> rewards;String effect="items",grant="",offerId=q.offer;
   boolean owned=!q.classId.isBlank()&&ClassProgression.owns(d,q.classId);
   if(q.offer.startsWith("class:")||q.offer.startsWith("bundle:")){
    if(!ClassSwitchService.selectable(q.classId)||!Objects.equals(config.classTiers.get(q.classId),q.tier))return reject(p,"changed");var tier=config.tiers.get(q.tier);
    if(q.offer.startsWith("class:")){if(owned||ClassProgression.starter(d))return reject(p,"owned_or_free");price=tier.classPrice;rewards=List.of();grant=q.classId;}
    else{if(owned!=q.equipmentOnly)return reject(p,"ownership_changed");if(!gearValid(q.classId))return reject(p,"equipment_unavailable");price=owned?tier.equipmentPrice:tier.bundlePrice;rewards=config.equipment.get(q.classId);if(!owned)grant=q.classId;}
   }else{
    var o=config.offers.stream().filter(v->v.id.equals(q.offer)&&v.enabled).findFirst().orElseThrow(()->new IllegalArgumentException("Offer disabled"));
    if("items".equals(o.category))CommercePolicy.validateItemPrices(o.price.beast,o.price.hunter);
    if(!o.permission.isBlank()&&!SVFrameMMO.permissions().has(p,o.permission))return reject(p,"requirement");if(o.limit>0&&d.getClaimCount("fantasyhub:purchases:"+o.id)>=o.limit)return reject(p,"limit");price=o.price;rewards=o.items;effect=o.effect;
   }
   var amount=price.get(method);CommercePolicy.positiveWhole(amount);
   // Reject predictable failures before generating RPG items or fsyncing an unreferenced mailbox.
   // paid() still revalidates the authoritative balance immediately before withdrawal.
   if(BUSY.contains(p.getUuid())||ClassProgression.busy(d)||ClassSwitchService.busy(d))return reject(p,"busy");
   var provider=CurrencyService.get();var selectedCurrency=CommerceCurrencies.payment(method);
   if(!provider.isAvailable(selectedCurrency)||!provider.has(p,selectedCurrency,amount))return reject(p,"balance");
   var generated=new ArrayList<String>();for(var r:rewards){var st=stack(r);generated.add(st.encode(p.getRegistryManager()).toString());}
   String entitlement=grant,fx=effect;
   String preparedMail=generated.isEmpty()?null:prepareMail(p,token,generated);
   boolean result=paid(p,token,offerId,CommerceCurrencies.payment(method),amount,()->{
    if(!entitlement.isBlank())ClassProgression.own(d,entitlement);
    if(preparedMail!=null)d.setPreference(MAIL,preparedMail);
    if(fx.equals("exp")){long prior=Long.parseLong(d.getPreference("fantasyhub:exp-boost-until","0"));d.setPreference("fantasyhub:exp-boost-until",Long.toString(Math.max(prior,System.currentTimeMillis())+1_800_000));}
    if(fx.equals("title"))d.setPreference("fantasyhub:title","Cosmic Hunter");
    d.setClaimCount("fantasyhub:purchases:"+offerId,d.getClaimCount("fantasyhub:purchases:"+offerId)+1);
    d.setClaimCount("fantasyhub:receipt:"+token,1);
   });
   if(result){QUOTES.remove(p.getUuid());p.sendMessage(Text.translatable("fantasyhub.commerce.success"),false);try{claim(p);}catch(RuntimeException delivery){LOG.error("Purchase {} committed for {}; mailbox delivery remains pending",token,p.getUuid(),delivery);p.sendMessage(Text.translatable("fantasyhub.commerce.delivery_pending"),false);}}return result;
  }catch(Exception e){LOG.warn("Purchase {} rejected for {} before commit; see transaction recovery if refund failed",token,p.getUuid(),e);return reject(p,"rejected");}
 }
 static boolean paid(ServerPlayerEntity p,String token,String product,CurrencyKey currency,BigDecimal amount,Runnable grant)throws Exception{
  var d=SVFrameMMO.playerData().get(p);if(BUSY.contains(p.getUuid())||ClassProgression.busy(d)||ClassSwitchService.busy(d))return reject(p,"busy");
  var service=CurrencyService.get();if(!service.isAvailable(currency)||!service.has(p,currency,amount))return reject(p,"balance");
  var journal=new ClassSwitchJournal(p.getServer().getSavePath(WorldSavePath.ROOT),p.getUuid());var previous=journal.read();if(previous!=null){if(previous.transactionId().equals(d.getPreference(RECEIPT,"")))journal.clear();else return reject(p,"reconcile");}
  long txStarted=System.nanoTime();BUSY.add(p.getUuid());var before=PlayerDataSnapshot.capture(d);boolean charged=false,committed=false,withdrawalUncertain=false;
  try{
   journal.prepare(new ClassSwitchJournal.Entry(token,d.getClassId(),"SHOP:"+product,currency.serialized(),amount.toPlainString(),service.balance(p,currency).toPlainString()));
   withdrawalUncertain=true;boolean withdrawn=service.withdraw(p,currency,amount);withdrawalUncertain=false;if(!withdrawn){journal.clear();return reject(p,"balance");}charged=true;
   grant.run();d.setPreference(RECEIPT,token);long saveAt=System.nanoTime();try{SVFrameMMO.playerData().savePlayerDurably(d).join();}finally{SAVE_TIMES.add(System.nanoTime()-saveAt);}committed=true;
   try{journal.clear();}catch(Exception cleanup){LOG.warn("Committed transaction journal retained: {}",token);}return true;
  }catch(Exception error){
   if(!committed){ClassProgression.restore(d,before);try{SVFrameMMO.playerData().savePlayerDurably(d).join();if(charged){/* Retain intent if refund throws; never automatically retry ambiguous deposit. */service.deposit(p,currency,amount);journal.clear();}else if(!withdrawalUncertain)journal.clear();else ClassProgression.quarantine(d);}catch(Exception recovery){ClassProgression.quarantine(d);error.addSuppressed(recovery);}}
   throw error;
  }finally{BUSY.remove(p.getUuid());TX_TIMES.add(System.nanoTime()-txStarted);}
 }
 public static void awardItemsOnce(ServerPlayerEntity p,String settlement,List<Reward> rewards){
  var d=SVFrameMMO.playerData().get(p);String marker="fantasyhub:item-settlement:"+settlement;if(d.getClaimCount(marker)>0)return;
  var generated=new ArrayList<String>();for(var r:rewards)generated.add(stack(r).encode(p.getRegistryManager()).toString());
  String preparedMail=prepareMail(p,settlement,generated);ClassProgression.transact(d,()->{d.setPreference(MAIL,preparedMail);d.setClaimCount(marker,1);});
 }
 private static MailboxStore mailbox(ServerPlayerEntity p){return new MailboxStore(p.getServer().getSavePath(WorldSavePath.ROOT).resolve("fantasyhub-mail"));}
 private static JsonArray mail(ServerPlayerEntity p){try{return JsonParser.parseString(mailbox(p).read(p.getUuid(),SVFrameMMO.playerData().get(p).getPreference(MAIL,"[]"))).getAsJsonArray();}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}
 private static String saveMail(ServerPlayerEntity p,JsonArray entries){try{return mailbox(p).write(p.getUuid(),JSON.toJson(entries));}catch(java.io.IOException error){throw new java.io.UncheckedIOException(error);}}
 private static String prepareMail(ServerPlayerEntity p,String receipt,List<String> generated){
  var box=mail(p);for(int i=0;i<generated.size();i++){var e=new JsonObject();e.addProperty("id",receipt+":"+i);e.addProperty("stack",generated.get(i));box.add(e);}return saveMail(p,box);
 }
 public static int claim(ServerPlayerEntity p){
  var d=SVFrameMMO.playerData().get(p);if(BUSY.contains(p.getUuid())||ClassProgression.busy(d)||ClassSwitchService.busy(d))return 0;
  var entries=mail(p);if(entries.isEmpty())return 0;var retained=new JsonArray();int delivered=0;
  for(var raw:entries){var e=raw.getAsJsonObject();String id=e.get("id").getAsString();boolean found=false;for(int i=0;i<p.getInventory().size();i++){var c=p.getInventory().getStack(i).get(DataComponentTypes.CUSTOM_DATA);if(c!=null&&id.equals(c.copyNbt().getString("fantasy_delivery"))){found=true;break;}}
   if(found){delivered++;continue;}
   if(p.getInventory().getEmptySlot()<0){retained.add(raw);continue;}
   try{var st=ItemStack.fromNbt(p.getRegistryManager(),StringNbtReader.parse(e.get("stack").getAsString())).orElseThrow();var n=st.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();n.putString("fantasy_delivery",id);st.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(n));p.getInventory().setStack(p.getInventory().getEmptySlot(),st);((vn.svframe.fantasyhub.protocol.mixin.PlayerSaveAccess)p.getServer().getPlayerManager()).svframe$savePlayer(p);delivered++;}catch(Exception error){retained.add(raw);LOG.error("Mailbox item retained for {}",p.getUuid(),error);}
  }
  if(delivered>0){var before=d.getPreference(MAIL,"[]");try{d.setPreference(MAIL,saveMail(p,retained));SVFrameMMO.playerData().savePlayerDurably(d).join();}catch(RuntimeException failure){d.setPreference(MAIL,before);ClassProgression.quarantine(d);throw failure;}}
  return delivered;
 }
 private static boolean reject(ServerPlayerEntity p,String s){p.sendMessage(Text.translatable("fantasyhub.commerce.error."+s),false);return false;}
}
