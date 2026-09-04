package vn.svframe.svframemmo.persistence;

import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframemmo.SVFrameMMO;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.api.player.profess.SavedClassState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Imports original MMOCore flat YAML userdata into the native persistence model. */
public final class LegacyYamlImporter {
    public record ImportResult(int imported, int skippedOnline, int failed, List<String> errors) {
        public ImportResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
        }
    }

    public ImportResult importDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory))
            throw new IOException("Legacy userdata directory does not exist: " + directory);

        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        int skippedOnline = 0, failed = 0;
        ArrayList<String> errors = new ArrayList<>();
        ArrayList<PreparedImport> prepared = new ArrayList<>();

        // Phase 1: parse and validate every eligible file before mutating live persistence.
        for (Path file : files) {
            try {
                UUID id = uuidFromFile(file);
                PlayerData existing = SVFrameMMO.playerData().find(id);
                if (existing != null && existing.isOnline()) {
                    skippedOnline++;
                    errors.add(file.getFileName() + ": player is online; legacy import requires that player to be offline");
                    continue;
                }
                LegacySnapshot snapshot = parse(file);
                snapshot.apply(PlayerData.blank(id));
                prepared.add(new PreparedImport(id, file, snapshot));
            } catch (Exception exception) {
                failed++;
                errors.add(file.getFileName() + ": " + exception.getMessage());
            }
        }

        // Parse/validation failures abort the directory transaction. Online players are intentionally skipped.
        if (failed > 0) {
            errors.add("Import aborted before apply because " + failed + " userdata file(s) failed validation.");
            return new ImportResult(0, skippedOnline, failed, errors);
        }

        // Phase 2: definitions are unchanged between validation and apply, so restore is deterministic.
        int imported = 0;
        for (PreparedImport entry : prepared) {
            PlayerData data = SVFrameMMO.playerData().get(entry.id());
            entry.snapshot().apply(data);
            SVFrameMMO.classSelection().markChosen(data);
            imported++;
        }
        if (imported > 0) SVFrameMMO.playerData().save();
        return new ImportResult(imported, skippedOnline, 0, errors);
    }

    private static LegacySnapshot parse(Path file) throws IOException {
        Map<String, Object> root;
        try { root = YamlLite.map(YamlLite.parse(file)); }
        catch (RuntimeException exception) { throw new IOException("Invalid YAML", exception); }
        var cfg = SVFrameMMO.config();

        if (!root.containsKey("class-points")) {
            return new LegacySnapshot(
                    SVFrameMMO.classes().getDefaultClass().getId(), cfg.defaultLevel(), 0d,
                    cfg.defaultClassPoints(), cfg.defaultSkillPoints(), cfg.defaultAttributePoints(),
                    cfg.defaultReallocationPoints(), cfg.defaultReallocationPoints(), cfg.defaultReallocationPoints(),
                    cfg.defaultHealth(), cfg.defaultMana(), cfg.defaultStamina(), cfg.defaultStellium(),
                    Map.of(), Map.of(), Map.of(), Set.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        String classId = string(root.get("class"), SVFrameMMO.classes().getDefaultClass().getId());
        if (SVFrameMMO.classes().get(classId) == null)
            throw new IOException("Unknown class '" + classId + "'");

        Map<String, Integer> professionLevels = new LinkedHashMap<>();
        Map<String, Double> professionExperience = new LinkedHashMap<>();
        map(root.get("profession")).forEach((id, raw) -> {
            var profession = SVFrameMMO.professions().get(id);
            if (profession == null) return;
            Map<String, Object> section = map(raw);
            professionLevels.put(profession.getId(), Math.max(1, integer(section.get("level"), 1)));
            professionExperience.put(profession.getId(), Math.max(0d, decimal(section.get("exp"), 0d)));
        });

        Map<String, SavedClassState> classSlots = new LinkedHashMap<>();
        map(root.get("class-info")).forEach((id, raw) -> {
            if (SVFrameMMO.classes().get(id) == null || !(raw instanceof Map<?, ?>)) return;
            classSlots.put(id, parseClassState(map(raw)));
        });

        LinkedHashMap<String, Integer> claims = new LinkedHashMap<>();
        flattenNumbers("", root.get("times-claimed"), claims);

        return new LegacySnapshot(
                classId,
                Math.max(1, integer(root.get("level"), cfg.defaultLevel())),
                Math.max(0d, decimal(root.get("experience"), 0d)),
                Math.max(0, integer(root.get("class-points"), cfg.defaultClassPoints())),
                Math.max(0, integer(root.get("skill-points"), cfg.defaultSkillPoints())),
                Math.max(0, integer(root.get("attribute-points"), cfg.defaultAttributePoints())),
                Math.max(0, integer(first(root, "attribute-realloc-points", "attribute-reallocation-points"), cfg.defaultReallocationPoints())),
                Math.max(0, integer(first(root, "skill-reallocation-points", "skill-realloc-points"), cfg.defaultReallocationPoints())),
                Math.max(0, integer(root.get("skill-tree-reallocation-points"), cfg.defaultReallocationPoints())),
                Math.max(0d, decimal(root.get("health"), cfg.defaultHealth())),
                Math.max(0d, decimal(root.get("mana"), cfg.defaultMana())),
                Math.max(0d, decimal(root.get("stamina"), cfg.defaultStamina())),
                Math.max(0d, decimal(root.get("stellium"), cfg.defaultStellium())),
                intMap(root.get("attribute")), intMap(root.get("skill")), bindings(root.get("bound-skills")),
                stringSet(root.get("unlocked-items")), claims,
                professionLevels, professionExperience,
                intMap(root.get("skill-tree-points")), intMap(root.get("skill-tree-level")), classSlots);
    }

    private static SavedClassState parseClassState(Map<String, Object> section) {
        var cfg = SVFrameMMO.config();
        LinkedHashMap<String, Integer> progressionClaims = new LinkedHashMap<>();
        flattenNumbers("", section.get("node-times-claimed"), progressionClaims);
        return new SavedClassState(
                Math.max(1, integer(section.get("level"), cfg.defaultLevel())),
                Math.max(0d, decimal(section.get("experience"), 0d)),
                Math.max(0, integer(section.get("skill-points"), cfg.defaultSkillPoints())),
                Math.max(0, integer(section.get("attribute-points"), cfg.defaultAttributePoints())),
                Math.max(0, integer(first(section, "attribute-realloc-points", "attribute-reallocation-points"), cfg.defaultReallocationPoints())),
                Math.max(0, integer(first(section, "skill-reallocation-points", "skill-realloc-points"), cfg.defaultReallocationPoints())),
                Math.max(0, integer(section.get("skill-tree-reallocation-points"), cfg.defaultReallocationPoints())),
                Math.max(0d, decimal(section.get("health"), 20d)),
                Math.max(0d, decimal(section.get("mana"), 0d)),
                Math.max(0d, decimal(section.get("stamina"), 0d)),
                Math.max(0d, decimal(section.get("stellium"), 0d)),
                intMap(section.get("attribute")), intMap(section.get("skill")), bindings(section.get("bound-skills")),
                stringSet(section.get("unlocked-items")), intMap(section.get("skill-tree-points")),
                intMap(section.get("node-levels")), progressionClaims);
    }

    private static UUID uuidFromFile(Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.indexOf('.');
        String raw = dot < 0 ? name : name.substring(0, dot);
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException exception) { throw new IOException("Filename is not a UUID: " + name, exception); }
    }

    private static Map<String, Integer> intMap(Object raw) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        map(raw).forEach((key, value) -> {
            int amount = integer(value, 0);
            if (amount != 0) result.put(key, amount);
        });
        return result;
    }

    private static Map<Integer, String> bindings(Object raw) {
        LinkedHashMap<Integer, String> result = new LinkedHashMap<>();
        map(raw).forEach((key, value) -> {
            try {
                int slot = Integer.parseInt(key);
                if (slot > 0 && value != null) result.put(slot, String.valueOf(value));
            } catch (NumberFormatException ignored) { }
        });
        return result;
    }

    private static Set<String> stringSet(Object raw) {
        if (!(raw instanceof Collection<?> values)) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (Object value : values) if (value != null && !String.valueOf(value).isBlank()) result.add(String.valueOf(value));
        return result;
    }

    private static void flattenNumbers(String prefix, Object raw, Map<String, Integer> out) {
        if (raw instanceof Map<?, ?> source) {
            source.forEach((key, value) -> flattenNumbers(prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key, value, out));
            return;
        }
        if (prefix.isEmpty() || raw == null) return;
        int amount = integer(raw, 0);
        if (amount > 0) out.put(prefix, amount);
    }

    private static Object first(Map<String, Object> map, String... keys) {
        for (String key : keys) if (map.containsKey(key)) return map.get(key);
        return null;
    }

    private static Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String string(Object raw, String fallback) { return raw == null ? fallback : String.valueOf(raw); }
    private static int integer(Object raw, int fallback) {
        try { return raw instanceof Number number ? number.intValue() : raw == null ? fallback : Integer.parseInt(String.valueOf(raw)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static double decimal(Object raw, double fallback) {
        try { return raw instanceof Number number ? number.doubleValue() : raw == null ? fallback : Double.parseDouble(String.valueOf(raw)); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private record PreparedImport(UUID id, Path file, LegacySnapshot snapshot) { }

    private record LegacySnapshot(
            String playerClass, int level, double experience,
            int classPoints, int skillPoints, int attributePoints,
            int attributeReallocationPoints, int skillReallocationPoints, int skillTreeReallocationPoints,
            double health, double mana, double stamina, double stellium,
            Map<String, Integer> attributes, Map<String, Integer> skills, Map<Integer, String> bindings,
            Set<String> unlockedItems, Map<String, Integer> claims,
            Map<String, Integer> professionLevels, Map<String, Double> professionExperience,
            Map<String, Integer> skillTreePoints, Map<String, Integer> skillTreeNodeLevels,
            Map<String, SavedClassState> classSlots) {
        private void apply(PlayerData data) {
            data.restore(playerClass, level, experience, classPoints, skillPoints, attributePoints,
                    attributeReallocationPoints, skillReallocationPoints, skillTreeReallocationPoints,
                    health, mana, stamina, stellium, attributes, skills, bindings, unlockedItems, claims,
                    professionLevels, professionExperience, skillTreePoints, skillTreeNodeLevels, classSlots);
        }
    }
}
