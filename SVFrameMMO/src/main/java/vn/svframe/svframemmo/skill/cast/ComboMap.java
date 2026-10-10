package vn.svframe.svframemmo.skill.cast;

import vn.svframe.svframelib.UtilityMethods;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Validated mapping between input sequences and bound skill slots. */
public final class ComboMap {
    private final Map<KeyCombo, Integer> combos = new LinkedHashMap<>();
    private final Set<PlayerKey> firstKeys = new LinkedHashSet<>();
    private final Set<PlayerKey> keys = new LinkedHashSet<>();
    private final int longestCombo;

    public ComboMap(Object raw) {
        if (!(raw instanceof Map<?, ?> section)) throw new IllegalArgumentException("Key combos require a configuration section");
        int longest = 0;
        for (Map.Entry<?, ?> entry : section.entrySet()) {
            try {
                int skillSlot = Integer.parseInt(String.valueOf(entry.getKey()));
                if (skillSlot < 0) throw new IllegalArgumentException("Skill slot must be at least 0");
                if (combos.containsValue(skillSlot)) throw new IllegalArgumentException("There is already a key combo with the same skill slot");
                if (!(entry.getValue() instanceof Collection<?> sequence) || sequence.isEmpty())
                    throw new IllegalArgumentException("Key combo cannot be empty");
                KeyCombo combo = new KeyCombo();
                for (Object value : sequence) {
                    PlayerKey key = PlayerKey.valueOf(UtilityMethods.enumName(String.valueOf(value)));
                    keys.add(key);
                    combo.registerKey(key);
                }
                if (combos.put(combo, skillSlot) != null) throw new IllegalArgumentException("Duplicate key combo");
                firstKeys.add(combo.getAt(0));
                longest = Math.max(longest, combo.countKeys());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Could not load key combo '" + entry.getKey() + "': " + exception.getMessage(), exception);
            }
        }
        longestCombo = longest;
    }

    public Map<KeyCombo, Integer> getCombos() { return Map.copyOf(combos); }
    public int getLongest() { return longestCombo; }
    public boolean isComboKey(PlayerKey key) { return keys.contains(key); }
    public boolean isComboStart(PlayerKey key) { return firstKeys.contains(key); }
    public boolean isEmpty() { return combos.isEmpty(); }
}
