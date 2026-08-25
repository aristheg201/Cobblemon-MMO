package vn.svframe.mythiclibfabric.runtime;

import java.util.EnumMap;
import java.util.Map;

public final class DamagePacket {
    private final EnumMap<DamageType, Double> parts = new EnumMap<>(DamageType.class);

    public DamagePacket add(DamageType type, double amount) {
        if (!Double.isFinite(amount) || amount < 0.0d) throw new IllegalArgumentException("invalid damage amount");
        parts.merge(type, amount, Double::sum);
        return this;
    }

    public double get(DamageType type) {
        return parts.getOrDefault(type, 0.0d);
    }

    public double total() {
        return parts.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public Map<DamageType, Double> parts() {
        return Map.copyOf(parts);
    }

    public DamagePacket copy() {
        DamagePacket copy = new DamagePacket();
        copy.parts.putAll(parts);
        return copy;
    }
}
