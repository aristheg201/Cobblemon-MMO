package vn.svframe.svframemmo.api.player.attribute;

import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.player.modifier.ModifierType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Native Fabric representation of an SVFrameMMO player attribute. */
public final class PlayerAttribute {
    public record Buff(String stat, ModifierType type, double value) {
        public Buff {
            stat = UtilityMethods.enumName(Objects.requireNonNull(stat, "stat"));
            type = Objects.requireNonNull(type, "type");
        }
    }

    private final String id;
    private final String name;
    private final int max;
    private final boolean save;
    private final List<Buff> buffs;

    public PlayerAttribute(String id, Map<String, Object> config) {
        this.id = normalizeId(id);
        Objects.requireNonNull(config, "config");
        this.name = String.valueOf(config.getOrDefault("name", "MyAttribute"));
        this.max = config.containsKey("max-points") ? Math.max(1, integer(config.get("max-points"), 1)) : 0;
        this.save = bool(config.get("save-to-player-data"), true);

        ArrayList<Buff> parsed = new ArrayList<>();
        Object rawBuffs = config.get("buff");
        if (rawBuffs instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                var pair = ModifierType.pairFromString(String.valueOf(entry.getValue()).trim());
                parsed.add(new Buff(String.valueOf(entry.getKey()), pair.getLeft(), pair.getRight()));
            }
        }
        this.buffs = List.copyOf(parsed);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean hasMax() { return max > 0; }
    public int getMax() { return max; }
    public boolean isSaved() { return save; }
    public List<Buff> getBuffs() { return buffs; }
    public String getKey() { return "attribute:" + id.replace('-', '_'); }

    public static String normalizeId(String value) {
        return Objects.requireNonNull(value, "attribute id").trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }

    private static int integer(Object value, int fallback) {
        try {
            return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : Boolean.parseBoolean(text);
    }
}
