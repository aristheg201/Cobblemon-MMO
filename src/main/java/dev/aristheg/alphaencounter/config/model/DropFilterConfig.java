package dev.aristheg.alphaencounter.config.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DropFilterConfig {
    public boolean enabled = true;
    public boolean blockAllItems = false;
    public List<String> blockedItems = new ArrayList<>(List.of(
        "cobblemon:health_candy",
        "cobblemon:mighty_candy",
        "cobblemon:tough_candy",
        "cobblemon:smart_candy",
        "cobblemon:courage_candy",
        "cobblemon:quick_candy",
        "cobblemon:sickly_candy",
        "cobblemon:weak_candy",
        "cobblemon:brittle_candy",
        "cobblemon:numb_candy",
        "cobblemon:coward_candy",
        "cobblemon:slow_candy"
    ));
    public Map<String, EncounterRule> encounters = new LinkedHashMap<>();

    public void normalize() {
        blockedItems = normalizeItems(blockedItems);
        if (encounters == null) encounters = new LinkedHashMap<>();
        encounters.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        for (EncounterRule rule : encounters.values()) rule.normalize();
    }

    public boolean blocks(String encounterId, String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        EncounterRule rule = encounterId == null ? null : encounters.get(encounterId);
        boolean effectiveEnabled = rule != null && rule.enabled != null ? rule.enabled : enabled;
        if (!effectiveEnabled) return false;
        boolean effectiveBlockAll = rule != null && rule.blockAllItems != null ? rule.blockAllItems : blockAllItems;
        if (effectiveBlockAll) return true;
        List<String> effectiveItems = rule != null && rule.blockedItems != null ? rule.blockedItems : blockedItems;
        String normalizedItem = itemId.toLowerCase(Locale.ROOT);
        for (String pattern : effectiveItems) {
            if (pattern.equals("*") || pattern.equals(normalizedItem)) return true;
            if (pattern.endsWith(":*") && normalizedItem.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
        }
        return false;
    }

    private static List<String> normalizeItems(List<String> values) {
        if (values == null) return new ArrayList<>();
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) continue;
            String item = value.trim().toLowerCase(Locale.ROOT);
            if (!item.isBlank()) normalized.add(item);
        }
        return new ArrayList<>(normalized);
    }

    public static final class EncounterRule {
        public Boolean enabled;
        public Boolean blockAllItems;
        public List<String> blockedItems;

        public void normalize() {
            if (blockedItems != null) blockedItems = normalizeItems(blockedItems);
        }
    }
}
