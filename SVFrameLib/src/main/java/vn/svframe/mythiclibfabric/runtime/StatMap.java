package vn.svframe.mythiclibfabric.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class StatMap {
    private final Map<String, Double> base = new HashMap<>();
    private final Map<String, List<StatModifier>> modifiers = new HashMap<>();

    public synchronized void setBase(String stat, double value) {
        base.put(normalize(stat), finite(value));
    }

    public synchronized double getBase(String stat) {
        return base.getOrDefault(normalize(stat), 0.0d);
    }

    public synchronized void put(StatModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        String stat = normalize(modifier.key());
        List<StatModifier> list = modifiers.computeIfAbsent(stat, ignored -> new ArrayList<>());
        list.removeIf(existing -> existing.source().equals(modifier.source()));
        list.add(modifier);
    }

    public synchronized boolean remove(String stat, String source) {
        List<StatModifier> list = modifiers.get(normalize(stat));
        if (list == null) return false;
        boolean removed = list.removeIf(mod -> mod.source().equals(source));
        if (list.isEmpty()) modifiers.remove(normalize(stat));
        return removed;
    }

    public synchronized int purgeExpired(long nowMillis) {
        int removed = 0;
        Iterator<Map.Entry<String, List<StatModifier>>> entries = modifiers.entrySet().iterator();
        while (entries.hasNext()) {
            List<StatModifier> list = entries.next().getValue();
            int before = list.size();
            list.removeIf(mod -> mod.expired(nowMillis));
            removed += before - list.size();
            if (list.isEmpty()) entries.remove();
        }
        return removed;
    }

    public synchronized double value(String stat, long nowMillis) {
        String key = normalize(stat);
        double baseValue = base.getOrDefault(key, 0.0d);
        Collection<StatModifier> all = modifiers.getOrDefault(key, List.of());
        double additive = 0.0d;
        double multiplyBase = 0.0d;
        double multiplyTotal = 1.0d;
        for (StatModifier mod : all) {
            if (mod.expired(nowMillis)) continue;
            switch (mod.operation()) {
                case ADD -> additive += mod.value();
                case MULTIPLY_BASE -> multiplyBase += mod.value();
                case MULTIPLY_TOTAL -> multiplyTotal *= 1.0d + mod.value();
            }
        }
        return finite((baseValue + additive + baseValue * multiplyBase) * multiplyTotal);
    }

    public synchronized List<StatModifier> modifiers(String stat) {
        return List.copyOf(modifiers.getOrDefault(normalize(stat), List.of()));
    }

    private static String normalize(String stat) {
        String normalized = Objects.requireNonNull(stat, "stat").trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("stat must not be blank");
        return normalized;
    }

    private static double finite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("stat value must be finite");
        return value;
    }
}
