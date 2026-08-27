package vn.svframe.svframeitems.model;

import java.util.*;

public record ItemDefinition(
        String id,
        String typeId,
        String materialId,
        String displayName,
        int revision,
        int defaultLevel,
        int minLevel,
        int maxLevel,
        Map<String,Integer> rarityWeights,
        List<StatRollSpec> stats,
        List<String> sockets,
        String setId,
        String upgradeTemplateId,
        List<ItemAbility> abilities,
        String gemColor
) {
    public ItemDefinition {
        id = ItemType.normalize(id);
        typeId = ItemType.normalize(typeId);
        materialId = Objects.requireNonNull(materialId, "materialId").trim().toLowerCase(Locale.ROOT);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        if (revision < 0) throw new IllegalArgumentException("revision must be >= 0");
        if (minLevel < 1 || maxLevel < minLevel) throw new IllegalArgumentException("invalid level range");
        if (defaultLevel < minLevel || defaultLevel > maxLevel) throw new IllegalArgumentException("default level outside range");
        Map<String,Integer> normalized = new LinkedHashMap<>();
        if (rarityWeights != null) for (Map.Entry<String,Integer> entry : rarityWeights.entrySet()) {
            int weight = Objects.requireNonNull(entry.getValue());
            if (weight < 0) throw new IllegalArgumentException("rarity weight must be >= 0");
            if (weight > 0) normalized.put(ItemType.normalize(entry.getKey()), weight);
        }
        if (normalized.isEmpty()) normalized.put("common", 1);
        rarityWeights = Map.copyOf(normalized);
        stats = stats == null ? List.of() : List.copyOf(stats);
        sockets = sockets == null ? List.of() : sockets.stream().map(EmbeddedGem::normalizeColor).toList();
        setId = setId == null || setId.isBlank() ? null : ItemType.normalize(setId);
        upgradeTemplateId = upgradeTemplateId == null || upgradeTemplateId.isBlank() ? null : ItemType.normalize(upgradeTemplateId);
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
        gemColor = gemColor == null || gemColor.isBlank() ? null : EmbeddedGem.normalizeColor(gemColor);
    }
    public int clampLevel(int requested) { return Math.max(minLevel, Math.min(maxLevel, requested)); }
    public boolean isGem() { return gemColor != null; }
}
