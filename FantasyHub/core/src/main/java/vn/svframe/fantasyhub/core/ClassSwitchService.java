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
        // Historical fee/currency keys remain on disk for backward compatibility only.
        // Class entitlement and bypass pricing now come exclusively from commerce.json.
        ClassPricing nextPricing=ClassPricing.parse(Map.of());
        long duration=Long.parseLong(String.valueOf(next.get("cooldown-seconds")));
        if(duration<0||duration>31_536_000)throw new IOException("Invalid class-switch policy");
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
        return legacyPermission(player,id);
    }
    static boolean legacyPermission(ServerPlayerEntity player,String id){
        if(id.equals("HUNTER"))return permission(player,text("hunter-permission"));
        if(id.equals("DRAGONBORN"))return permission(player,text("dragonborn-permission"))
                ||list("supporter-permissions").stream().anyMatch(p->permission(player,p))
                ||(!list("dragonborn-story-permissions").isEmpty()&&list("dragonborn-story-permissions").stream().allMatch(p->permission(player,p)));
        return permission(player,text("permission-prefix")+id.toLowerCase(Locale.ROOT));
    }
    static Map<String,Object> policy(PlayerData data) {
        var price=new ClassPricing.Price(BigDecimal.ZERO,"beconomy:secondary");var key=CurrencyKey.parse(price.currency());var service=CurrencyService.get();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("beastBalance",service.isAvailable(CurrencyKey.parse("beconomy:secondary"))?service.balance(data.getPlayer(),CurrencyKey.parse("beconomy:secondary")).toPlainString():"Unavailable");
        out.put("bypassFee",CommerceRuntime.bypassPrice().toPlainString());out.put("bypassCurrency","BeastCoin");
        out.put("starter",ClassProgression.starter(data));out.put("sigils",ClassProgression.sigils(data));out.put("sigilStorage","banked");
        out.put("fee",price.amount().toPlainString());out.put("cooldownSeconds",Long.parseLong(text("cooldown-seconds")));
        out.put("availableAt",data.getClassSwitchAvailableAt());out.put("remainingSeconds",ClassProgression.starter(data)||!ClassProgression.settings().switchCooldownEnabled()?0L:Math.max(0,(data.getClassSwitchAvailableAt()-System.currentTimeMillis()+999)/1000));
        out.put("currency",price.label());out.put("economyAvailable",service.isAvailable(key));out.put("busy",pending.contains(data.getUniqueId())||ClassProgression.busy(data));
        return out;
    }
    static Map<String,Object> offer(String id,ServerPlayerEntity player) { return offer(id,player,new HashMap<>()); }
    static Map<String,Object> offer(String id,ServerPlayerEntity player,Map<CurrencyKey,BigDecimal> balances) {
        return Map.of("fee","0","currency","Miễn phí chuyển","currencyKey","free","economyAvailable",true,"canAfford",true);
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
    static boolean request(ServerPlayerEntity player,String id,Runnable invalidate) { return request(player,id,false,invalidate); }
    static boolean request(ServerPlayerEntity player,String id,boolean bypass,Runnable invalidate) {
        id=id.toUpperCase(Locale.ROOT);
        PlayerData data=SVFrameMMO.playerData().get(player);PlayerClass target=SVFrameMMO.classes().get(id);
        if(target==null||!selectable(target.getId()))return reject(player,"invalid");
        if(!starterSafe(player,data,target.getId()))return reject(player,"locked");
        if(data.getClassId().equals(target.getId()))return reject(player,"current");
        boolean starter=ClassProgression.starter(data);
        if(busy(data)||ClassProgression.busy(data))return reject(player,"busy");
        boolean cooling=!starter&&((Number)policy(data).get("remainingSeconds")).longValue()>0;
        if(cooling&&!bypass)return reject(player,"cooldown");
        var currency=CurrencyKey.parse("beconomy:secondary");var service=CurrencyService.get();
        BigDecimal fee=cooling?CommerceRuntime.bypassPrice():BigDecimal.ZERO;
        boolean paid=fee.signum()>0;
        if(paid&&!service.isAvailable(currency))return reject(player,"economy");
        if(paid&&!service.has(player,currency,fee))return reject(player,"funds");
        UUID uuid=player.getUuid();pending.add(uuid);
        var before=PlayerDataSnapshot.capture(data);
        var journal=journal(player);
        boolean charged=false,prepared=false,committed=false,refundUncertain=false;
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
                if(charged){refundUncertain=true;service.deposit(player,currency,fee);refundUncertain=false;charged=false;}
                if(prepared){journal.clear();prepared=false;}
                return reject(player,"cancelled");
            }
            if(starter)ClassProgression.consumeStarter(data,target.getId());
            else ClassProgression.own(data,target.getId());
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
                if(charged&&rollbackSaved&&!refundUncertain)try {
                    service.deposit(player,currency,fee);charged=false;
                    if(prepared){journal.clear();prepared=false;}
                }catch(IOException|RuntimeException refundFailure){failure.addSuppressed(refundFailure);}
                // Retain uncertain two-store transactions for reconciliation; never guess and debit twice on reconnect.
            }
            LOG.log(Level.SEVERE,"Class-switch transaction failed (recovery journal="+prepared+"): "+uuid,failure);
            return reject(player,"save_failed");
        } finally {pending.remove(uuid);invalidate.run();}
    }
    private static boolean starterSafe(ServerPlayerEntity player,PlayerData data,String id){return ClassProgression.starter(data)||ClassProgression.owns(data,id)||unlocked(player,id);}
    private static boolean reject(ServerPlayerEntity player,String reason){player.sendMessage(Text.translatable("fantasyhub.classes.error."+reason),true);return false;}
}
