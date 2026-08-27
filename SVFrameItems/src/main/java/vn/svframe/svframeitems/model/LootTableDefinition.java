package vn.svframe.svframeitems.model;

import java.util.*;

public record LootTableDefinition(String id, int rolls, List<Entry> entries) {
    public record Entry(String itemId, int weight, double chance, int minAmount, int maxAmount, int minLevel, int maxLevel) {
        public Entry {
            itemId = ItemType.normalize(itemId);
            if (weight < 1) throw new IllegalArgumentException("weight must be >= 1");
            if (!Double.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("chance must be 0..1");
            if (minAmount < 1 || maxAmount < minAmount) throw new IllegalArgumentException("invalid amount range");
            if (minLevel < 1 || maxLevel < minLevel) throw new IllegalArgumentException("invalid level range");
        }
    }
    public LootTableDefinition {
        id = ItemType.normalize(id);
        if (rolls < 1) throw new IllegalArgumentException("rolls must be >= 1");
        entries = entries == null ? List.of() : List.copyOf(entries);
        if (entries.isEmpty()) throw new IllegalArgumentException("loot table needs entries");
    }
}
