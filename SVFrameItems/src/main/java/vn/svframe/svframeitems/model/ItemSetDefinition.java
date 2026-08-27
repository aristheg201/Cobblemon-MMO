package vn.svframe.svframeitems.model;

import java.util.*;

public record ItemSetDefinition(String id, String displayName, Set<String> pieces, NavigableMap<Integer,List<ItemStat>> bonuses) {
    public ItemSetDefinition {
        id = ItemType.normalize(id);
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        Set<String> normalizedPieces = new LinkedHashSet<>();
        if (pieces != null) for (String piece : pieces) normalizedPieces.add(ItemType.normalize(piece));
        pieces = Set.copyOf(normalizedPieces);
        TreeMap<Integer,List<ItemStat>> normalizedBonuses = new TreeMap<>();
        if (bonuses != null) for (Map.Entry<Integer,List<ItemStat>> entry : bonuses.entrySet()) {
            if (entry.getKey() < 1) throw new IllegalArgumentException("set threshold must be >= 1");
            normalizedBonuses.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        bonuses = Collections.unmodifiableNavigableMap(normalizedBonuses);
    }
    public List<ItemStat> activeBonuses(int equippedPieces) {
        List<ItemStat> out = new ArrayList<>();
        for (Map.Entry<Integer,List<ItemStat>> entry : bonuses.headMap(equippedPieces, true).entrySet()) out.addAll(entry.getValue());
        return List.copyOf(out);
    }
}
