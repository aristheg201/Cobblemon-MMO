package vn.svframe.svframeitems.model;

import java.util.*;

public record UpgradeTemplate(
        String id,
        int maxLevel,
        double baseSuccessChance,
        double successDecay,
        boolean destroyOnFail,
        double statMultiplierPerLevel,
        Map<Integer,Double> explicitChances
) {
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
