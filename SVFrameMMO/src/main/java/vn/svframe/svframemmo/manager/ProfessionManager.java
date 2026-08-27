package vn.svframe.svframemmo.manager;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.experience.Profession;
import vn.svframe.svframemmo.experience.curve.ExperienceCurveRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Fail-fast native profession registry. */
public final class ProfessionManager {
    private final Map<String, Profession> professions = new LinkedHashMap<>();

    public void reload(Path directory, ExperienceCurveRegistry curves) throws IOException {
        professions.clear();
        if (!Files.isDirectory(directory)) throw new IOException("Missing profession directory: " + directory);
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".yml")).sorted().toList()) {
                Map<String, Object> config = YamlLite.map(YamlLite.parse(path));
                String file = path.getFileName().toString();
                String id = file.substring(0, file.length() - 4);
                register(new Profession(id, config, curves));
            }
        }
        if (professions.isEmpty()) throw new IOException("No native profession definitions found in " + directory);
    }

    public void register(Profession profession) {
        Objects.requireNonNull(profession, "profession");
        Profession previous = professions.putIfAbsent(normalize(profession.getId()), profession);
        if (previous != null) throw new IllegalStateException("Duplicate profession ID '" + profession.getId() + "'");
    }

    public Profession get(String id) { return id == null ? null : professions.get(normalize(id)); }
    public Profession getOrThrow(String id) {
        Profession found = get(id);
        if (found == null) throw new IllegalArgumentException("Could not find profession with ID '" + id + "'");
        return found;
    }
    public boolean has(String id) { return get(id) != null; }
    public int size() { return professions.size(); }
    public Collection<Profession> getAll() { return List.copyOf(professions.values()); }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }
}
