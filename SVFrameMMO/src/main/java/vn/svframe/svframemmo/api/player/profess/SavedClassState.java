package vn.svframe.svframemmo.api.player.profess;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable per-class progression snapshot. This is the native equivalent of the
 * legacy saved-class container, without storage/platform concerns.
 */
public record SavedClassState(
        int level,
        double experience,
        int skillPoints,
        int attributePoints,
        int attributeReallocationPoints,
        int skillReallocationPoints,
        int skillTreeReallocationPoints,
        double health,
        double mana,
        double stamina,
        double stellium,
        Map<String, Integer> attributes,
        Map<String, Integer> skills,
        Map<Integer, String> bindings,
        Set<String> unlockedItems,
        Map<String, Integer> skillTreePoints,
        Map<String, Integer> skillTreeNodeLevels,
        Map<String, Integer> progressionClaims) {

    public SavedClassState {
        level = Math.max(1, level);
        experience = Math.max(0d, experience);
        skillPoints = Math.max(0, skillPoints);
        attributePoints = Math.max(0, attributePoints);
        attributeReallocationPoints = Math.max(0, attributeReallocationPoints);
        skillReallocationPoints = Math.max(0, skillReallocationPoints);
        skillTreeReallocationPoints = Math.max(0, skillTreeReallocationPoints);
        health = Math.max(0d, health);
        mana = Math.max(0d, mana);
        stamina = Math.max(0d, stamina);
        stellium = Math.max(0d, stellium);
        attributes = immutableIntMap(attributes);
        skills = immutableIntMap(skills);
        bindings = bindings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(bindings));
        unlockedItems = unlockedItems == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(unlockedItems));
        skillTreePoints = immutableIntMap(skillTreePoints);
        skillTreeNodeLevels = immutableIntMap(skillTreeNodeLevels);
        progressionClaims = immutableIntMap(progressionClaims);
    }

    public int spentSkillPoints() {
        return skills.values().stream().mapToInt(level -> Math.max(0, level - 1)).sum();
    }

    public int spentAttributePoints() {
        return attributes.values().stream().mapToInt(level -> Math.max(0, level)).sum();
    }

    private static Map<String, Integer> immutableIntMap(Map<String, Integer> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, Integer> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
