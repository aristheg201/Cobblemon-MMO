package vn.svframe.svframemmo.experience.source;

import vn.svframe.svframelib.config.YamlLite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads reusable MMOCore-style {@code from{source=...}} experience-source groups. */
public final class ExperienceSourceGroups {
    private ExperienceSourceGroups() { }

    public static Map<String, List<String>> load(Path file) throws IOException {
        Map<String, Object> root = YamlLite.map(YamlLite.parse(file));
        LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : root.entrySet()) {
            String id = normalize(entry.getKey());
            if (id.isBlank()) throw new IOException("Blank experience-source group ID in " + file);
            if (!(entry.getValue() instanceof List<?> raw))
                throw new IOException("Experience-source group '" + entry.getKey() + "' must be a YAML list");
            ArrayList<String> lines = new ArrayList<>();
            for (Object value : raw) {
                if (value == null || String.valueOf(value).isBlank()) continue;
                lines.add(String.valueOf(value).trim());
            }
            List<String> previous = groups.putIfAbsent(id, List.copyOf(lines));
            if (previous != null) throw new IOException("Duplicate experience-source group: " + id);
        }
        return Map.copyOf(groups);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase().replace('_', '-').replace(' ', '-');
    }
}
