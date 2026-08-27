package vn.svframe.svframemmo.experience.curve;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ExperienceCurveRegistry {
    private final Map<String, ExperienceCurve> curves = new LinkedHashMap<>();

    public void reload(Path directory) throws IOException {
        curves.clear();
        if (directory == null || !Files.isDirectory(directory)) return;
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.filter(Files::isRegularFile).sorted().toList()) {
                String fileName = path.getFileName().toString();
                if (fileName.startsWith(".")) continue;
                ArrayList<Long> values = new ArrayList<>();
                for (String raw : Files.readAllLines(path)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    values.add(Long.parseLong(line));
                }
                if (!values.isEmpty()) curves.put(normalize(stripExtension(fileName)), new ListExperienceCurve(values));
            }
        }
    }

    public void register(String id, ExperienceCurve curve) {
        curves.put(normalize(id), curve);
    }

    public ExperienceCurve get(String id) { return id == null ? null : curves.get(normalize(id)); }

    public ExperienceCurve getOrThrow(String id) {
        ExperienceCurve found = get(id);
        if (found == null) throw new IllegalArgumentException("Could not find experience curve with ID '" + id + "'");
        return found;
    }

    public ExperienceCurve fromConfig(String input) {
        if (input == null || input.isBlank()) return ExperienceCurve.DEFAULT;
        ExperienceCurve named = get(input);
        return named == null ? new FormulaExperienceCurve(input) : named;
    }

    public int size() { return curves.size(); }

    private static String stripExtension(String value) {
        int dot = value.lastIndexOf('.');
        return dot <= 0 ? value : value.substring(0, dot);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
    }
}
