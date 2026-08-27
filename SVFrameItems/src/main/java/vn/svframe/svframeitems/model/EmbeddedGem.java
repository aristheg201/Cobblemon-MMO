package vn.svframe.svframeitems.model;

import java.util.*;

public record EmbeddedGem(
        UUID instanceId, String definitionId, String typeId, String rarityId,
        int itemLevel, int upgradeLevel, int definitionRevision, long seed, long stateRevision,
        String color, List<ItemStat> stats, Map<String,String> metadata
) {
    public EmbeddedGem {
        Objects.requireNonNull(instanceId, "instanceId");
        definitionId = ItemType.normalize(definitionId); typeId = ItemType.normalize(typeId); rarityId = ItemType.normalize(rarityId);
        if (itemLevel < 1 || upgradeLevel < 0 || definitionRevision < 0 || stateRevision < 0) throw new IllegalArgumentException("invalid embedded gem state");
        color = normalizeColor(color); stats = stats == null ? List.of() : List.copyOf(stats);
        Map<String,String> normalized = new LinkedHashMap<>();
        if (metadata != null) metadata.forEach((key,value) -> normalized.put(ItemInstance.normalizeMetadataKey(key), Objects.requireNonNull(value, "metadata value")));
        metadata = Map.copyOf(normalized);
    }
    public EmbeddedGem(UUID instanceId, String definitionId, String typeId, String rarityId,
                       int itemLevel, int upgradeLevel, int definitionRevision, long seed, long stateRevision,
                       String color, List<ItemStat> stats) {
        this(instanceId, definitionId, typeId, rarityId, itemLevel, upgradeLevel, definitionRevision, seed, stateRevision, color, stats, Map.of());
    }
    public static EmbeddedGem from(ItemInstance item, String color) {
        return new EmbeddedGem(item.instanceId(), item.definitionId(), item.typeId(), item.rarityId(), item.itemLevel(), item.upgradeLevel(), item.definitionRevision(), item.seed(), item.stateRevision(), color, item.stats(), item.metadata());
    }
    public ItemInstance toItemInstance() { return new ItemInstance(instanceId, definitionId, typeId, rarityId, itemLevel, upgradeLevel, definitionRevision, seed, stateRevision, stats, List.of(), metadata); }
    public static String normalizeColor(String value) { return value == null || value.isBlank() ? "any" : value.trim().toLowerCase(Locale.ROOT); }
}
