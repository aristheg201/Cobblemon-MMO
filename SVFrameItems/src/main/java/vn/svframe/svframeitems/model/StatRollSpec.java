package vn.svframe.svframeitems.model;

import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import java.util.random.RandomGenerator;
import java.util.*;

public record StatRollSpec(String stat, double min, double max, double perLevel, int decimals, NativeStatEngine.ModifierType type) {
    public StatRollSpec {
        stat = Objects.requireNonNull(stat, "stat").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!Double.isFinite(min) || !Double.isFinite(max) || !Double.isFinite(perLevel)) throw new IllegalArgumentException("stat roll values must be finite");
        if (max < min) throw new IllegalArgumentException("max < min for " + stat);
        if (decimals < 0 || decimals > 6) throw new IllegalArgumentException("decimals must be 0..6");
        Objects.requireNonNull(type, "type");
    }
    public ItemStat roll(int itemLevel, RandomGenerator random) {
        int level = Math.max(1, itemLevel);
        double bonus = perLevel * (level - 1);
        double value = min == max ? min : min + random.nextDouble() * (max - min);
        value += bonus;
        double scale = Math.pow(10, decimals);
        value = Math.round(value * scale) / scale;
        return new ItemStat(stat, value, type);
    }
}
