package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.handler.StatHandler;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;
import vn.svframe.mythiclibfabric.MythicLibStatMod;
import vn.svframe.mythiclibfabric.runtime.NativeStatHandler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Public 1.7.1 stat manager backed by the native Fabric stat engine. */
public class StatManager extends Module implements MMOManager {
    private final Map<String,StatHandler> handlers=new LinkedHashMap<>();
    public StatManager(MMOPlugin plugin){super(plugin,"stat");}
    public static String format(String stat,MMOPlayerData data){return format(stat,data.getStatMap().getStat(stat));}
    public static String format(String stat,double value){NativeStatHandler h=MythicLibStatMod.engine().handler(stat);return h==null?new java.text.DecimalFormat("0.#").format(value):h.format(value);}
    public synchronized void registerStat(StatHandler handler,String...aliases){String key=UtilityMethods.enumName(handler.getStat());handlers.put(key,handler);for(String alias:aliases)handlers.put(UtilityMethods.enumName(alias),handler);}
    public synchronized StatHandler computeStat(String stat){String key=UtilityMethods.enumName(stat);StatHandler existing=handlers.get(key);if(existing!=null)return existing;NativeStatHandler n=MythicLibStatMod.engine().handler(key);StatHandler created=n==null?new StatHandler(key):new StatHandler(key,n.configuredBaseValue(),n.hasMinValue()?n.minValue():null,n.hasMaxValue()?n.maxValue():null,n.decimalFormat());handlers.put(key,created);return created;}
    public Optional<StatHandler> getHandler(String stat){String key=UtilityMethods.enumName(stat);StatHandler h=handlers.get(key);if(h==null&&MythicLibStatMod.engine().handler(key)!=null)h=computeStat(key);return Optional.ofNullable(h);}
    public boolean isRegistered(String stat){return getHandler(stat).isPresent();}
    public Set<String> getRegisteredStats(){for(NativeStatHandler h:MythicLibStatMod.engine().handlers())computeStat(h.stat());return Set.copyOf(handlers.keySet());}
    public Collection<StatHandler> getHandlers(){getRegisteredStats();return List.copyOf(new LinkedHashSet<>(handlers.values()));}
    @Override public void initialize(boolean clear){if(clear)handlers.clear();getRegisteredStats();}
    public void clearRegisteredStats(Predicate<StatHandler> predicate){handlers.entrySet().removeIf(e->predicate.test(e.getValue()));}
    public void runUpdate(StatMap map,String stat){runUpdate(map.getInstance(stat));}
    public void runUpdates(StatMap map){for(StatInstance instance:map.getInstances())runUpdate(instance);}
    public void runUpdate(StatInstance instance){getHandler(instance.getStat()).orElseGet(()->computeStat(instance.getStat())).runUpdates(instance);}
    public double getBaseValue(String stat,StatMap map){return getBaseValue(map.getInstance(stat));}
    public double getBaseValue(StatInstance instance){return getHandler(instance.getStat()).orElseGet(()->computeStat(instance.getStat())).getBaseValue(instance);}
    public double getTotalValue(String stat,StatMap map){return getTotalValue(map.getInstance(stat));}
    public double getTotalValue(StatInstance instance){return getHandler(instance.getStat()).orElseGet(()->computeStat(instance.getStat())).clampValue(instance.getTotal());}
    public void registerStat(String stat,StatHandler handler){handlers.put(UtilityMethods.enumName(stat),handler);}
}
