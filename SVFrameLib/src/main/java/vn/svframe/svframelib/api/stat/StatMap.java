package vn.svframe.svframelib.api.stat;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.api.player.EquipmentSlot;
import vn.svframe.svframelib.api.player.MMOPlayerData;
import vn.svframe.svframelib.api.stat.handler.StatHandler;
import vn.svframe.svframelib.api.stat.provider.PlayerStatProvider;
import vn.svframe.svframelib.player.PlayerDataMap;
import vn.svframe.svframelib.player.PlayerMetadata;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Player stat container with the exact session/buffer lifecycle used by SVFrameLib 1.7.1. */
public class StatMap extends PlayerDataMap implements PlayerStatProvider {
    private final MMOPlayerData data;
    private final Map<String, StatInstance> stats = new ConcurrentHashMap<>();
    private final AtomicInteger updatesBuffered = new AtomicInteger(0);

    public StatMap(MMOPlayerData data) {
        this.data = data;
    }

    @Override
    public MMOPlayerData getData() {
        return data;
    }

    @Override
    public EquipmentSlot getActionHand() {
        return EquipmentSlot.MAIN_HAND;
    }

    @Override
    public double getStat(String stat) {
        return getInstance(stat).getFinal();
    }

    public StatInstance getInstance(String stat) {
        return stats.computeIfAbsent(stat, key -> new StatInstance(this, key));
    }

    public Collection<StatInstance> getInstances() {
        return stats.values();
    }

    @Override
    public void onSessionOpen() {
        for (StatHandler handler : SVFrameLib.plugin.getStats().getHandlers()) {
            StatInstance instance = handler.updateOnLogin()
                    ? getInstance(handler.getStat())
                    : stats.get(handler.getStat());
            if (instance == null) continue;
            instance.invalidateReferences();
            instance.update();
        }
    }

    @Override
    protected void onSessionClose() {
        invalidateReferences();
    }

    public void invalidateReferences() {
        stats.values().forEach(StatInstance::invalidateReferences);
    }

    public boolean isBufferingUpdates() {
        return updatesBuffered.get() > 0 || !sessionOpen;
    }

    public void bufferUpdates(Runnable runnable) {
        updatesBuffered.incrementAndGet();
        try {
            runnable.run();
        } finally {
            int left = updatesBuffered.decrementAndGet();
            if (left == 0 && sessionOpen) stats.values().forEach(StatInstance::releaseUpdates);
        }
    }

    @Override
    public PlayerMetadata cache(EquipmentSlot actionHand) {
        return new PlayerMetadata(this, actionHand);
    }

    public void update(String stat) {
        StatInstance instance = stats.get(stat);
        if (instance != null) instance.update();
    }

    /** @deprecated retained for the 1.7.1 API surface. */
    @Deprecated
    public MMOPlayerData getPlayerData() {
        return data;
    }
}
