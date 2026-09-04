package vn.svframe.svframemmo.skill.cast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Ordered key sequence used by KEY_COMBOS. */
public final class KeyCombo {
    private final List<PlayerKey> keys = new ArrayList<>();

    public int countKeys() { return keys.size(); }
    public void registerKey(PlayerKey key) { keys.add(Objects.requireNonNull(key, "key")); }
    public PlayerKey getAt(int index) { return keys.get(index); }
    public List<PlayerKey> keys() { return List.copyOf(keys); }

    @Override public boolean equals(Object object) { return object instanceof KeyCombo other && keys.equals(other.keys); }
    @Override public int hashCode() { return keys.hashCode(); }
}
