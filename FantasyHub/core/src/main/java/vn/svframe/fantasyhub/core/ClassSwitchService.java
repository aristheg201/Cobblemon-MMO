package vn.svframe.fantasyhub.core;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.svframelib.api.economy.CurrencyService;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.event.PlayerClassChangeEvent;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;
import vn.svframe.svframelib.api.economy.CurrencyKey;
import java.nio.file.Files;
import vn.svframe.svframemmo.persistence.PlayerDataSnapshot;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.*;

/** Native adapter of the supplied sv_class_switch.sk policy, using the canonical MMO character. */
final class ClassSwitchService {
    private static final Logger LOG=Logger.getLogger("FantasyHub-ClassSwitch");
    private static final Set<UUID> pending=new HashSet<>();
    private static Map<String,Object> config=Map.of();
    private static ClassPricing pricing;
    static void reload() throws IOException {
        var path=FabricLoader.getInstance().getConfigDir().resolve("FantasyHub/class-switch.yml");
        var next=new LinkedHashMap<String,Object>(HeroRosterMigration.migrate(path,RiderRosterMigration.migrate(path,YamlLite.map(YamlLite.parse(path)))));
        if(!Objects.toString(next.get("pricing-version"),"1").equals("2")) {
            next.put("pricing-version",2);next.put("fee",3000);next.put("currency","beconomy:secondary");
            next.put("premium-currency","beconomy:primary");next.put("premium-fee",2);
            Files.copy(path,path.resolveSibling("class-switch.before-pricing-v2.yml"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            String original=Files.readString(path).replaceAll("(?m)^(pricing-version|currency|premium-currency|premium-fee):[^\\r\\n]*(\\r?\\n|$)","").replaceAll("(?m)^fee:.*$","fee: 3000");
            Files.writeString(path,original+"\npricing-version: 2\ncurrency: beconomy:secondary\npremium-currency: beconomy:primary\npremium-fee: 2\n");
            LOG.info("Migrated class prices: 3000 beastcoin; Dragonborn/Death Knight 2 huntercoin. General MMO economy unchanged.");
        }
        ClassPricing nextPricing=ClassPricing.parse(next);
        BigDecimal fee=new BigDecimal(String.valueOf(next.get("fee")));
        long duration=Long.parseLong(String.valueOf(next.get("cooldown-seconds")));
        if(fee.signum()<0||duration<0||duration>31_536_000)throw new IOException("Invalid class-switch policy");
        config=Map.copyOf(next);pricing=nextPricing;
        ClassScriptMigration.migrate(FabricLoader.getInstance().getConfigDir());
    }
    private static List<String> list(String key) {
        Object raw=config.get(key);if(!(raw instanceof List<?> values))return List.of();
        return values.stream().map(String::valueOf).toList();
    }
    private static String text(String key){return Objects.toString(config.get(key),"");}
    private static boolean permission(ServerPlayerEntity player,String key){return SVFrameMMO.permissions().has(player,key);}
    static boolean busy(PlayerData data) { return pending.contains(data.getUniqueId()); }
    static boolean selectable(String id){
        var type=SVFrameMMO.classes().get(id);
        if(type==null||type.hasOption(vn.svframe.svframemmo.api.player.profess.ClassOption.DEFAULT))return false;
        var script=vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.classPolicy();return list("additional-native-classes").contains(id)||(script!=null?script.selectable(id):list("selectable-classes").contains(id));}
    static boolean unlocked(ServerPlayerEntity player,String id) {
        if(!selectable(id))return false;
        var data=SVFrameMMO.playerData().get(player);
        if(ClassProgression.starter(data)||ClassProgression.owns(data,id))return true;
        if(id.equals("HUNTER"))return permission(player,text("hunter-permission"));
        if(id.equals("DRAGONBORN"))return permission(player,text("dragonborn-permission"))
                ||list("supporter-permissions").stream().anyMatch(p->permission(player,p))
                ||(!list("dragonborn-story-permissions").isEmpty()&&list("dragonborn-story-permissions").stream().allMatch(p->permission(player,p)));
        return permission(player,text("permission-prefix")+id.toLowerCase(Locale.ROOT));
    }
    static Map<String,Object> policy(PlayerData data) {
        var price=pricing.price(data.getClassId());var key=CurrencyKey.parse(price.currency());var service=CurrencyService.get();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("starter",ClassProgression.starter(data));out.put("sigils",ClassProgression.sigils(data));out.put("sigilStorage","banked");
        out.put("fee",price.amount().toPlainString());out.put("cooldownSeconds",Long.parseLong(text("cooldown-seconds")));
        out.put("availableAt",data.getClassSwitchAvailableAt());out.put("remainingSeconds",ClassProgression.starter(data)||!ClassProgression.settings().switchCooldownEnabled()?0L:Math.max(0,(data.getClassSwitchAvailableAt()-System.currentTimeMillis()+999)/1000));
        out.put("currency",price.label());out.put("economyAvailable",service.isAvailable(key));out.put("busy",pending.contains(data.getUniqueId())||ClassProgression.busy(data));
        var script=vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.classPolicy();if(script!=null&&data.isOnline()&&!ClassProgression.starter(data)&&ClassProgression.settings().switchCooldownEnabled()) {var legacy=script.details(data.getPlayer());
            for(String field:List.of("availableAt","remainingSeconds"))if(legacy.get(field) instanceof Number value)out.put(field,Math.max(((Number)out.get(field)).longValue(),value.longValue()));
        }
        return out;
    }
    static Map<String,Object> offer(String id,ServerPlayerEntity player) { return offer(id,player,new HashMap<>()); }
    static Map<String,Object> offer(String id,ServerPlayerEntity player,Map<CurrencyKey,BigDecimal> balances) {
        if(ClassProgression.starter(SVFrameMMO.playerData().get(player))||!ClassProgression.settings().switchFeesEnabled())return Map.of("fee","0","currency",ClassProgression.starter(SVFrameMMO.playerData().get(player))?"Lượt đầu miễn phí":"Miễn phí chuyển","currencyKey","free","economyAvailable",true,"canAfford",true);
        var price=pricing.price(id);var key=CurrencyKey.parse(price.currency());var service=CurrencyService.get();
        boolean available=price.amount().signum()==0||service.isAvailable(key);
        return Map.of("fee",price.amount().toPlainString(),"currency",price.label(),"currencyKey",key.serialized(),"economyAvailable",available,
                "canAfford",price.amount().signum()==0||available&&balances.computeIfAbsent(key,k->service.balance(player,k)).compareTo(price.amount())>=0);
    }
    private static ClassSwitchJournal journal(ServerPlayerEntity player) {
        return new ClassSwitchJournal(Objects.requireNonNull(player.getServer()).getSavePath(net.minecraft.util.WorldSavePath.ROOT),player.getUuid());
    }
    private static boolean recover(ServerPlayerEntity player,PlayerData data) throws IOException {
        var journal=journal(player);var entry=journal.read();
        if(entry==null)return true;
        if(entry.transactionId().equals(data.getPreference("fantasyhub:last-paid-switch",""))) {journal.clear();return true;}
        player.sendMessage(Text.literal("Giao dịch chuyển class đang cần quản trị đối soát. /fantasyprogress status"),false);
        return false;
    }
    static String recoveryStatus(ServerPlayerEntity player) throws IOException {
        var entry=journal(player).read();return entry==null?"Không có giao dịch cần đối soát.":new com.google.gson.Gson().toJson(entry);
    }
    static void reconcile(ServerPlayerEntity player,boolean refund) throws IOException {
        var journal=journal(player);var entry=journal.read();if(entry==null)return;
        var data=SVFrameMMO.playerData().get(player);
        if(!entry.transactionId().equals(data.getPreference("fantasyhub:last-paid-switch",""))&&refund)
            CurrencyService.get().deposit(player,CurrencyKey.parse(entry.currency()),new BigDecimal(entry.amount()));
        journal.clear();
    }
    static boolean request(ServerPlayerEntity player,String id,Runnable invalidate) {
        id=id.toUpperCase(Locale.ROOT);
        PlayerData data=SVFrameMMO.playerData().get(player);PlayerClass target=SVFrameMMO.classes().get(id);
        if(target==null||!selectable(target.getId()))return reject(player,"invalid");
        if(!unlocked(player,target.getId()))return reject(player,"locked");
        if(data.getClassId().equals(target.getId()))return reject(player,"current");
        boolean starter=ClassProgression.starter(data);
        if(busy(data)||ClassProgression.busy(data))return reject(player,"busy");
        if(!starter&&((Number)policy(data).get("remainingSeconds")).longValue()>0)return reject(player,"cooldown");
        var selectedPrice=pricing.price(target.getId());var currency=CurrencyKey.parse(selectedPrice.currency());var service=CurrencyService.get();
        BigDecimal fee=starter||!ClassProgression.settings().switchFeesEnabled()?BigDecimal.ZERO:selectedPrice.amount();
        boolean paid=fee.signum()>0;
        if(paid&&!service.isAvailable(currency))return reject(player,"economy");
        if(paid&&!service.has(player,currency,fee))return reject(player,"funds");
        UUID uuid=player.getUuid();pending.add(uuid);
        var before=PlayerDataSnapshot.capture(data);
        var journal=journal(player);
        boolean charged=false,prepared=false,committed=false;
        try {
            if(!recover(player,data))return false;
            String transactionId=UUID.randomUUID().toString();
            if(paid) {
                journal.prepare(new ClassSwitchJournal.Entry(transactionId,data.getClassId(),target.getId(),currency.serialized(),fee.toPlainString(),service.balance(player,currency).toPlainString()));
                prepared=true;
                if(!service.withdraw(player,currency,fee)){journal.clear();prepared=false;return reject(player,"funds");}
                charged=true;
            }
            if(!data.changeClass(target,PlayerClassChangeEvent.Reason.GUI)) {
                if(charged){service.deposit(player,currency,fee);charged=false;}
                if(prepared){journal.clear();prepared=false;}
                return reject(player,"cancelled");
            }
            if(starter)ClassProgression.consumeStarter(data,target.getId());
            ClassProgression.initialize(data);
            // The free initial choice incurs neither the old cooldown nor a new one.
            data.setClassSwitchAvailableAt(starter||!ClassProgression.settings().switchCooldownEnabled()?0L:
                    Math.addExact(System.currentTimeMillis(),Math.multiplyExact(Long.parseLong(text("cooldown-seconds")),1000L)));
            if(paid)data.setPreference("fantasyhub:last-paid-switch",transactionId);
            data.setPreference("fantasyhub:last-transaction",transactionId);
            // Native persistence has an independent writer; wait before exposing the changed class to another tick.
            SVFrameMMO.playerData().savePlayerDurably(data).join();
            committed=true;
            if(prepared)try{journal.clear();}catch(IOException cleanupFailure){LOG.log(Level.WARNING,"Committed class journal will be cleaned on next access: "+uuid,cleanupFailure);}
            try{SVFrameMMO.classSelection().markChosen(data);}catch(RuntimeException flagFailure){LOG.log(Level.WARNING,"Legacy class-selection marker failed; native starter claim is authoritative: "+uuid,flagFailure);}
            player.sendMessage(Text.translatable("fantasyhub.classes.changed",target.getName()),false);
            return true;
        } catch(IOException|RuntimeException failure) {
            if(!committed) {
                ClassProgression.restore(data,before);
                boolean rollbackSaved=false;
                try{SVFrameMMO.playerData().savePlayerDurably(data).join();rollbackSaved=true;}
                catch(RuntimeException rollbackFailure){ClassProgression.quarantine(data);failure.addSuppressed(rollbackFailure);}
                if(charged&&rollbackSaved)try {
                    service.deposit(player,currency,fee);charged=false;
                    if(prepared){journal.clear();prepared=false;}
                }catch(IOException|RuntimeException refundFailure){failure.addSuppressed(refundFailure);}
                // Retain uncertain two-store transactions for reconciliation; never guess and debit twice on reconnect.
            }
            LOG.log(Level.SEVERE,"Class-switch transaction failed (recovery journal="+prepared+"): "+uuid,failure);
            return reject(player,"save_failed");
        } finally {pending.remove(uuid);invalidate.run();}
    }
    private static boolean reject(ServerPlayerEntity player,String reason){player.sendMessage(Text.translatable("fantasyhub.classes.error."+reason),true);return false;}
}
