package vn.svframe.svframeitems.model;

import vn.svframe.svframelib.fabric.runtime.NativeStatEngine;
import java.util.*;

public record ItemStat(String stat, double value, NativeStatEngine.ModifierType type) {
    public ItemStat {
        stat = Objects.requireNonNull(stat, "stat").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (stat.isEmpty()) throw new IllegalArgumentException("stat cannot be empty");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("value must be finite");
        Objects.requireNonNull(type, "type");
    }
    public ItemStat scaled(double multiplier) { return new ItemStat(stat, value * multiplier, type); }
}
