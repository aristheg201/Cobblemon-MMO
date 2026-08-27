package vn.svframe.svframeitems.model;

public record ItemRarity(String id, String displayName, int weight, int priority, double statMultiplier) implements Comparable<ItemRarity> {
    public ItemRarity {
        id = ItemType.normalize(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        if (weight < 0) throw new IllegalArgumentException("weight must be >= 0");
        if (!Double.isFinite(statMultiplier) || statMultiplier < 0d) throw new IllegalArgumentException("statMultiplier must be finite and >= 0");
    }
    public ItemRarity(String id, String displayName, int weight, int priority) {
        this(id, displayName, weight, priority, 1d);
    }
    @Override public int compareTo(ItemRarity other) { return Integer.compare(priority, other.priority); }
}
