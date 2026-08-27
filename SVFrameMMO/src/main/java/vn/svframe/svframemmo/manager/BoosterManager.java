package vn.svframe.svframemmo.manager;

import vn.svframe.svframemmo.experience.Booster;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SVFrameMMO-compatible additive booster registry. */
public final class BoosterManager {
    private final List<Booster> active = new ArrayList<>();

    public synchronized void register(Booster booster) {
        flush();
        for (Booster current : active) if (current.canStackWith(booster)) {
            current.addDuration(booster.getDuration());
            return;
        }
        active.add(booster);
    }

    public synchronized boolean unregister(UUID id) {
        flush();
        return active.removeIf(booster -> booster.getUniqueId().equals(id));
    }

    public synchronized double multiplier(String targetKey) {
        flush();
        double multiplier = 1d;
        for (Booster booster : active) if (Objects.equals(targetKey, booster.getTargetKey())) multiplier += booster.getExtra();
        return multiplier;
    }

    public synchronized List<Booster> getActive() { flush(); return List.copyOf(active); }
    private void flush() { active.removeIf(Booster::isTimedOut); }
}
