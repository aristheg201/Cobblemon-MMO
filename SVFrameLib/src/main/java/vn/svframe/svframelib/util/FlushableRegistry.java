package vn.svframe.svframelib.util;

import vn.svframe.mythiclibfabric.MythicLibFabricMod;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

public class FlushableRegistry<K, V> implements Closeable {
    private final Map<K, V> registry = new HashMap<>();
    private final BiPredicate<K, V> condition;
    private final long period;
    private boolean open = true;

    public FlushableRegistry(BiPredicate<K, V> condition, long period) {
        this.condition = Objects.requireNonNull(condition);
        if (period < 1L) throw new IllegalArgumentException("Period must be positive");
        this.period = period;
        scheduleNext();
    }

    public Map<K, V> getRegistry() { return registry; }
    public boolean isOpen() { return open; }
    public long getPeriod() { return period; }

    @Override
    public void close() {
        if (!open) throw new IllegalArgumentException("Registry already closed");
        open = false;
        registry.clear();
    }

    private void scheduleNext() {
        MythicLibFabricMod.schedule((int) Math.min(Integer.MAX_VALUE, period), () -> {
            if (!open) return;
            registry.entrySet().removeIf(entry -> condition.test(entry.getKey(), entry.getValue()));
            scheduleNext();
        });
    }
}
