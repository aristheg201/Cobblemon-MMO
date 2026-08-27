package vn.svframe.svframeitems.model;

import java.util.*;

public record UpgradeTemplate(
        String id,
        int maxLevel,
        double baseSuccessChance,
        double successDecay,
        boolean destroyOnFail,
        double statMultiplierPerLevel,
        Map<Integer,Double> explicitChances,
        List<Cost> costs
) {
    public record Cost(String provider, String id, int amount, int perLevel) {
        public Cost {
            provider = ItemType.normalize(provider);
            id = Objects.requireNonNull(id, "cost id").trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) throw new IllegalArgumentException("cost id cannot be empty");
            if (amount < 0 || perLevel < 0) throw new IllegalArgumentException("cost amounts must be >= 0");
            if (amount == 0 && perLevel == 0) throw new IllegalArgumentException("cost must have a positive amount");
        }
        public int amountForNextLevel(int currentLevel) {
            long value = (long) amount + (long) Math.max(0, currentLevel) * perLevel;
            if (value > Integer.MAX_VALUE) throw new IllegalStateException("Upgrade cost overflow for " + provider + ':' + id);
            return (int) value;
        }
    }
    public UpgradeTemplate {
        id = ItemType.normalize(id);
        if (maxLevel < 1) throw new IllegalArgumentException("maxLevel must be >= 1");
        if (!validChance(baseSuccessChance)) throw new IllegalArgumentException("baseSuccessChance must be 0..1");
        if (!Double.isFinite(successDecay) || successDecay < 0) throw new IllegalArgumentException("successDecay must be finite and >= 0");
        if (!Double.isFinite(statMultiplierPerLevel) || statMultiplierPerLevel < -1) throw new IllegalArgumentException("invalid stat multiplier");
        Map<Integer,Double> normalized = new LinkedHashMap<>();
        if (explicitChances != null) explicitChances.forEach((level,chance) -> {
            if (level < 1 || level > maxLevel) throw new IllegalArgumentException("explicit chance level out of range");
            if (!validChance(chance)) throw new IllegalArgumentException("explicit chance must be 0..1");
            normalized.put(level, chance);
        });
        explicitChances = Map.copyOf(normalized);
        costs = costs == null ? List.of() : List.copyOf(costs);
    }
    public UpgradeTemplate(String id, int maxLevel, double baseSuccessChance, double successDecay, boolean destroyOnFail,
                           double statMultiplierPerLevel, Map<Integer,Double> explicitChances) {
        this(id, maxLevel, baseSuccessChance, successDecay, destroyOnFail, statMultiplierPerLevel, explicitChances, List.of());
    }
    public double chanceForNextLevel(int currentLevel) {
        int next = currentLevel + 1;
        if (next < 1 || next > maxLevel) return 0d;
        Double explicit = explicitChances.get(next);
        if (explicit != null) return explicit;
        return Math.max(0d, Math.min(1d, baseSuccessChance * Math.pow(successDecay, Math.max(0, currentLevel))));
    }
    private static boolean validChance(double value) { return Double.isFinite(value) && value >= 0d && value <= 1d; }
}
