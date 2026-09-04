package vn.svframe.svframemmo.skill.cast;

import vn.svframe.svframelib.UtilityMethods;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** One casting input plus an optional sneak-state requirement. */
public final class Keybind {
    private final PlayerKey key;
    private final Boolean sneak;

    public Keybind(Object raw) {
        if (raw instanceof Map<?, ?> section) {
            Map<String, Object> config = stringMap(section);
            key = PlayerKey.valueOf(UtilityMethods.enumName(String.valueOf(config.getOrDefault("key", "NONE"))));
            Object rawSneak = config.get("sneak");
            sneak = rawSneak == null ? null : rawSneak instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(rawSneak));
            return;
        }
        if (raw != null && !(raw instanceof Collection<?>)) {
            key = PlayerKey.valueOf(UtilityMethods.enumName(String.valueOf(raw)));
            sneak = null;
            return;
        }
        throw new IllegalArgumentException("Keybind requires a scalar key name or configuration section");
    }

    public Keybind(PlayerKey key, Boolean sneak) {
        this.key = Objects.requireNonNull(key, "key");
        this.sneak = sneak;
    }

    public PlayerKey key() { return key; }
    public Boolean sneak() { return sneak; }
    public boolean matches(PlayerKey pressed, boolean sneaking) { return pressed == key && (sneak == null || sneak == sneaking); }

    public static Keybind fromConfig(Object raw) { return raw == null ? null : new Keybind(raw); }

    private static Map<String, Object> stringMap(Map<?, ?> raw) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
