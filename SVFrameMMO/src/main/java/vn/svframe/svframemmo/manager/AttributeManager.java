package vn.svframe.svframemmo.manager;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.api.player.attribute.PlayerAttribute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Native attribute registry matching the 1.13.1 class/attribute config contract. */
public final class AttributeManager {
    private final Map<String, PlayerAttribute> values = new LinkedHashMap<>();

    public void reload(Path dir) throws IOException {
        values.clear();
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".yml")).sorted().toList()) {
                Map<String, Object> root = YamlLite.map(YamlLite.parse(path));
                for (Map.Entry<String, Object> entry : root.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> rawMap)) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> raw = (Map<String, Object>) rawMap;
                    registerAttribute(new PlayerAttribute(entry.getKey(), raw));
                }
            }
        }
    }

    public void registerAttribute(PlayerAttribute attribute) {
        String id = attribute.getId();
        if (values.containsKey(id)) throw new IllegalArgumentException("Found existing attribute with ID '" + id + "'");
        values.put(id, attribute);
    }

    public PlayerAttribute get(String id) {
        return id == null ? null : values.get(normalize(id));
    }

    public PlayerAttribute getOrThrow(String id) {
        PlayerAttribute found = get(id);
        if (found == null) throw new IllegalArgumentException("Could not find attribute with ID '" + id + "'");
        return found;
    }

    public boolean has(String id) { return get(id) != null; }
    public Collection<PlayerAttribute> getAll() { return List.copyOf(values.values()); }
    public int size() { return values.size(); }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }
}
