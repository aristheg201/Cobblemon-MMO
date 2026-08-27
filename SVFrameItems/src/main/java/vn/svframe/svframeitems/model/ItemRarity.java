package vn.svframe.svframeitems.model;

import java.util.*;

public record ItemRarity(String id, String displayName, int weight, int priority) implements Comparable<ItemRarity> {
    public ItemRarity {
        id = ItemType.normalize(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        if (weight < 0) throw new IllegalArgumentException("weight must be >= 0");
    }
    @Override public int compareTo(ItemRarity other) { return Integer.compare(priority, other.priority); }
}
