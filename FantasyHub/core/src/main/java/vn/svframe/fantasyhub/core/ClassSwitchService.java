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
import vn.svframe.svframemmo.economy.EconomyRuntime;
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
    static void reload() throws IOException {
        var next=YamlLite.map(YamlLite.parse(FabricLoader.getInstance().getConfigDir().resolve("FantasyHub/class-switch.yml")));
        BigDecimal fee=new BigDecimal(String.valueOf(next.get("fee")));
        long duration=Long.parseLong(String.valueOf(next.get("cooldown-seconds")));
        if(fee.signum()<0||duration<0||duration>31_536_000)throw new IOException("Invalid class-switch policy");
        config=Map.copyOf(next);
    }
    private static List<String> list(String key) {
        Object raw=config.get(key);if(!(raw instanceof List<?> values))return List.of();
        return values.stream().map(String::valueOf).toList();
    }
    private static String text(String key){return Objects.toString(config.get(key),"");}
    private static boolean permission(ServerPlayerEntity player,String key){return SVFrameMMO.permissions().has(player,key);}
    static boolean selectable(String id){var script=vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.classPolicy();return script!=null?script.selectable(id):list("selectable-classes").contains(id)||list("additional-native-classes").contains(id);}
    static boolean unlocked(ServerPlayerEntity player,String id) {
        if(!selectable(id))return false;
        if(id.equals("HUNTER"))return permission(player,text("hunter-permission"));
        if(id.equals("DRAGONBORN"))return permission(player,text("dragonborn-permission"))
                ||list("supporter-permissions").stream().anyMatch(p->permission(player,p))
                ||(!list("dragonborn-story-permissions").isEmpty()&&list("dragonborn-story-permissions").stream().allMatch(p->permission(player,p)));
        return permission(player,text("permission-prefix")+id.toLowerCase(Locale.ROOT));
    }
    static Map<String,Object> policy(PlayerData data) {
        var key=EconomyRuntime.instance().currency();var service=CurrencyService.get();
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("fee",text("fee"));out.put("cooldownSeconds",Long.parseLong(text("cooldown-seconds")));
        out.put("availableAt",data.getClassSwitchAvailableAt());out.put("remainingSeconds",Math.max(0,(data.getClassSwitchAvailableAt()-System.currentTimeMillis()+999)/1000));
        out.put("currency",key.serialized());out.put("economyAvailable",service.isAvailable(key));out.put("busy",pending.contains(data.getUniqueId()));
        var script=vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.classPolicy();if(script!=null&&data.isOnline())out.putAll(script.details(data.getPlayer()));
        return out;
    }
    static boolean request(ServerPlayerEntity player,String id,Runnable invalidate) {
        var script=vn.svframe.svframelib.fabric.ScriptCompatibilityHooks.classPolicy();if(script!=null){boolean result=script.request(player,id);invalidate.run();return result;}
        PlayerData data=SVFrameMMO.playerData().get(player);PlayerClass target=SVFrameMMO.classes().get(id);
        if(target==null||!selectable(target.getId()))return reject(player,"invalid");
        if(!unlocked(player,target.getId()))return reject(player,"locked");
        if(data.getClassId().equals(target.getId()))return reject(player,"current");
        if(pending.contains(player.getUuid()))return reject(player,"busy");
        if(data.getClassSwitchAvailableAt()>System.currentTimeMillis())return reject(player,"cooldown");
        var currency=EconomyRuntime.instance().currency();var service=CurrencyService.get();
        if(!service.isAvailable(currency))return reject(player,"economy");
        BigDecimal fee=new BigDecimal(text("fee"));
        if(!service.has(player,currency,fee))return reject(player,"funds");
        var before=PlayerDataSnapshot.capture(data);UUID uuid=player.getUuid();pending.add(uuid);
        boolean charged=false,changed=false;
        try {
            if(!service.withdraw(player,currency,fee)){pending.remove(uuid);return reject(player,"funds");}
            charged=true;
            if(!data.changeClass(target,PlayerClassChangeEvent.Reason.GUI)) {
                service.deposit(player,currency,fee);charged=false;pending.remove(uuid);return reject(player,"cancelled");
            }
            changed=true;
            data.setClassSwitchAvailableAt(Math.addExact(System.currentTimeMillis(),Math.multiplyExact(Long.parseLong(text("cooldown-seconds")),1000L)));
            invalidate.run();
            var server=Objects.requireNonNull(player.getServer());
            SVFrameMMO.playerData().savePlayerDurably(data).whenComplete((ignored,failure)->server.execute(()->{
                try {
                    if(failure==null) {
                        SVFrameMMO.classSelection().markChosen(data);
                        if(data.isOnline())data.getPlayer().sendMessage(Text.translatable("fantasyhub.classes.changed",target.getName()),false);
                    } else {
                        rollback(data,before);
                        try { service.deposit(player,currency,fee); }
                        catch(RuntimeException refundFailure){failure.addSuppressed(refundFailure);LOG.log(Level.SEVERE,"Class-switch refund requires reconciliation: "+uuid,refundFailure);}
                        SVFrameMMO.playerData().savePlayerDurably(data);
                        if(data.isOnline())reject(data.getPlayer(),"save_failed");
                        LOG.log(Level.SEVERE,"Class-switch persistence failed: "+uuid,failure);
                    }
                } finally {pending.remove(uuid);invalidate.run();}
            }));
            return true;
        } catch(RuntimeException failure) {
            if(changed)rollback(data,before);
            if(charged)try{service.deposit(player,currency,fee);}catch(RuntimeException refundFailure){failure.addSuppressed(refundFailure);}
            pending.remove(uuid);invalidate.run();LOG.log(Level.SEVERE,"Class-switch transaction failed: "+uuid,failure);
            return reject(player,"save_failed");
        }
    }
    private static void rollback(PlayerData data,PlayerDataSnapshot before) {
        // The character remains the sole owner of stats. Rebuild native modifiers
        // through its normal lifecycle rather than restoring cosmetic HUD values.
        double currentHealth=data.isOnline()?data.getPlayer().getHealth():data.getHealth();
        data.prepareReload();before.apply(data);data.reloadDefinitions();
        if(data.isOnline())data.getPlayer().setHealth((float)Math.min(currentHealth,data.getPlayer().getMaxHealth()));
    }
    private static boolean reject(ServerPlayerEntity player,String reason){player.sendMessage(Text.translatable("fantasyhub.classes.error."+reason),true);return false;}
}
