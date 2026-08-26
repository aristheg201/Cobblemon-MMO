package io.lumine.mythic.lib.manager;

import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.api.player.MMOPlayerData;
import io.lumine.mythic.lib.api.stat.StatInstance;
import io.lumine.mythic.lib.api.stat.StatMap;
import io.lumine.mythic.lib.api.stat.handler.StatHandler;
import io.lumine.mythic.lib.api.stat.modifier.StatModifier;
import io.lumine.mythic.lib.module.MMOPlugin;
import io.lumine.mythic.lib.module.Module;
import vn.svframe.mythiclibfabric.MythicLibStatMod;
import vn.svframe.mythiclibfabric.runtime.NativeStatEngine;
import vn.svframe.mythiclibfabric.runtime.NativeStatHandler;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Public 1.7.1 stat manager backed by the native Fabric stat engine. */
public class StatManager extends Module implements MMOManager {
    private final Map<String,StatHandler> handlers = new LinkedHashMap<>();

    public StatManager(MMOPlugin plugin) { super(plugin, "stat"); }

    public static String format(String stat, MMOPlayerData data) { return format(stat, data.getStatMap().getStat(stat)); }
    public static String format(String stat, double value) {
        NativeStatHandler nativeHandler = MythicLibStatMod.engine().handler(stat);
        return nativeHandler == null ? new java.text.DecimalFormat("0.#").format(value) : nativeHandler.format(value);
    }

    public synchronized void registerStat(StatHandler handler, String... aliases) {
        String key = UtilityMethods.enumName(handler.getStat());
        handlers.put(key, handler);
        for (String alias : aliases) handlers.put(UtilityMethods.enumName(alias), handler);
        NativeStatHandler bridge = new NativeStatHandler(handler.getStat(), handler.getBaseValue(null), null, null, handler.getDecimalFormat()) {
            @Override public double getBaseValue(NativeStatEngine.StatInstance ignored) { return handler.getBaseValue(null); }
            @Override public double getPlayerDefaultBase() { return handler.getPlayerDefaultBase(); }
            @Override public double getFinalValue(NativeStatEngine.StatInstance instance, NativeStatEngine.EquipmentSlot hand) {
                return clampValue(instance.total(hand));
            }
            @Override public boolean updateOnLogin() { return handler.updateOnLogin(); }
        };
        MythicLibStatMod.engine().registerHandler(bridge);
    }

    public synchronized StatHandler computeStat(String stat) {
        String key = UtilityMethods.enumName(stat);
        StatHandler existing = handlers.get(key);
        if (existing != null) return existing;
        NativeStatHandler nativeHandler = MythicLibStatMod.engine().handler(key);
        StatHandler created = nativeHandler == null
                ? new StatHandler(key)
                : new StatHandler(key, nativeHandler.configuredBaseValue(), nativeHandler.hasMinValue()?nativeHandler.minValue():null,
                    nativeHandler.hasMaxValue()?nativeHandler.maxValue():null, nativeHandler.decimalFormat());
        handlers.put(key, created);
        return created;
    }

    public Optional<StatHandler> getHandler(String stat) {
        String key = UtilityMethods.enumName(stat);
        StatHandler handler = handlers.get(key);
        if (handler == null && MythicLibStatMod.engine().handler(key) != null) handler = computeStat(key);
        return Optional.ofNullable(handler);
    }
    public boolean isRegistered(String stat) { return getHandler(stat).isPresent(); }
    public Set<String> getRegisteredStats() {
        for (NativeStatHandler h : MythicLibStatMod.engine().handlers()) computeStat(h.stat());
        return Set.copyOf(handlers.keySet());
    }
    public Collection<StatHandler> getHandlers() { getRegisteredStats(); return List.copyOf(new java.util.LinkedHashSet<>(handlers.values())); }
    @Override public void initialize(boolean clear) { if (clear) handlers.clear(); getRegisteredStats(); }
    public void clearRegisteredStats(Predicate<StatHandler> predicate) { handlers.entrySet().removeIf(e -> predicate.test(e.getValue())); }

    public void runUpdate(StatMap map, String stat) { StatInstance instance = map.getInstance(stat); runUpdate(instance); }
    public void runUpdates(StatMap map) { for (StatInstance instance : map.getInstances()) runUpdate(instance); }
    public void runUpdate(StatInstance instance) {
        StatHandler handler = getHandler(instance.getStat()).orElseGet(() -> computeStat(instance.getStat()));
        handler.runUpdates(instance);
    }
    public double getBaseValue(String stat, StatMap map) { return getBaseValue(map.getInstance(stat)); }
    public double getBaseValue(StatInstance instance) { return getHandler(instance.getStat()).orElseGet(() -> computeStat(instance.getStat())).getBaseValue(instance); }
    public double getTotalValue(String stat, StatMap map) { return getTotalValue(map.getInstance(stat)); }
    public double getTotalValue(StatInstance instance) { return getHandler(instance.getStat()).orElseGet(() -> computeStat(instance.getStat())).clampValue(instance.getTotal()); }
    public void registerStat(String stat, StatHandler handler) { handlers.put(UtilityMethods.enumName(stat), handler); }
}
