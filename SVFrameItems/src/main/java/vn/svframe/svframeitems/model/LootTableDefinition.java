package vn.svframe.svframeitems.model;

import java.util.*;

public record LootTableDefinition(String id, int rolls, List<Entry> entries) {
    public record Entry(String itemId, int weight, double chance, int minAmount, int maxAmount, int minLevel, int maxLevel,
                        String conditionId, String rewardId) {
        public Entry {
            itemId = Objects.requireNonNull(itemId, "itemId").trim().toLowerCase(Locale.ROOT);
            if (itemId.isEmpty()) throw new IllegalArgumentException("itemId cannot be empty");
            if (weight < 1) throw new IllegalArgumentException("weight must be >= 1");
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be 0..1");
            if (minAmount < 1 || maxAmount < minAmount) throw new IllegalArgumentException("invalid amount range");
            if (minLevel < 1 || maxLevel < minLevel) throw new IllegalArgumentException("invalid level range");
            conditionId = conditionId == null || conditionId.isBlank() ? "always" : ItemType.normalize(conditionId);
            rewardId = rewardId == null || rewardId.isBlank() ? "item" : ItemType.normalize(rewardId);
            if (rewardId.equals("item")) itemId = ItemType.normalize(itemId);
        }
        public Entry(String itemId, int weight, double chance, int minAmount, int maxAmount, int minLevel, int maxLevel) {
            this(itemId, weight, chance, minAmount, maxAmount, minLevel, maxLevel, "always", "item");
        }
        public int clampLevel(int level) { return Math.max(minLevel, Math.min(maxLevel, level)); }
        public int rollAmount(java.util.random.RandomGenerator random) {
            return minAmount == maxAmount ? minAmount : minAmount + random.nextInt(maxAmount - minAmount + 1);
        }
    }
    public LootTableDefinition {
        id = ItemType.normalize(id);
        if (rolls < 1) throw new IllegalArgumentException("rolls must be >= 1");
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.isEmpty()) throw new IllegalArgumentException("loot table needs entries");
    }
}
