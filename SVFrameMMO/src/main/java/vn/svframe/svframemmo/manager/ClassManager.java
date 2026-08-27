package vn.svframe.svframemmo.manager;

import vn.svframe.svframelib.SVFrameLib;
import vn.svframe.svframelib.UtilityMethods;
import vn.svframe.svframelib.config.YamlLite;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframemmo.api.player.profess.ClassOption;
import vn.svframe.svframemmo.api.player.profess.PlayerClass;
import vn.svframe.svframemmo.experience.curve.ExperienceCurveRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/** Native class registry with two-phase subclass resolution. */
public final class ClassManager {
    private static final Logger LOG = Logger.getLogger("SVFrameMMO-ClassManager");
    private final Map<String, PlayerClass> classes = new LinkedHashMap<>();
    private final Map<String, ScalingFormula> defaultStats = new LinkedHashMap<>();
    private final ExperienceCurveRegistry curves = new ExperienceCurveRegistry();
    private PlayerClass defaultClass;

    public void reload(Path classDirectory, Path statsFile, Path curveDirectory, boolean passiveSkillsNeedBinding) throws IOException {
        classes.clear();
        defaultStats.clear();
        defaultClass = null;
        loadDefaultStats(statsFile);
        curves.reload(curveDirectory);

        if (Files.isDirectory(classDirectory)) {
            try (var stream = Files.walk(classDirectory)) {
                for (Path path : stream.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".yml")).sorted().toList()) {
                    Map<String, Object> raw = YamlLite.map(YamlLite.parse(path));
                    String id = UtilityMethods.enumName(path.getFileName().toString().replaceFirst("[.]yml$", ""));
                    register(new PlayerClass(id, raw, defaultStats, curves, SVFrameLib.inst().getSkills(), passiveSkillsNeedBinding));
                }
            }
        }

        for (PlayerClass playerClass : classes.values()) {
            playerClass.resolveSubclasses(id -> {
                PlayerClass found = get(id);
                if (found == null) LOG.warning("Could not resolve subclass '" + id + "' from class '" + playerClass.getId() + "'");
                return found;
            });
        }

        defaultClass = classes.values().stream().filter(playerClass -> playerClass.hasOption(ClassOption.DEFAULT)).findFirst().orElse(null);
        if (defaultClass == null) defaultClass = get("HUMAN");
        if (defaultClass == null) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("display", Map.of("name", "Human", "item", "LEATHER_BOOTS"));
            fallback.put("options", Map.of("display", false, "default", false));
            defaultClass = new PlayerClass("HUMAN", fallback, defaultStats, curves, SVFrameLib.inst().getSkills(), passiveSkillsNeedBinding);
        }
    }

    public void register(PlayerClass playerClass) {
        Objects.requireNonNull(playerClass, "playerClass");
        classes.put(playerClass.getId(), playerClass);
    }

    public boolean has(String id) { return get(id) != null; }
    public PlayerClass get(String id) { return id == null ? null : classes.get(UtilityMethods.enumName(id)); }

    public PlayerClass getOrThrow(String id) {
        PlayerClass found = get(id);
        if (found == null) throw new IllegalArgumentException("Could not find class with ID '" + id + "'");
        return found;
    }

    public Collection<PlayerClass> getAll() { return List.copyOf(classes.values()); }
    public PlayerClass getDefaultClass() { return Objects.requireNonNull(defaultClass, "Class registry is not initialized"); }
    public int size() { return classes.size(); }
    public ExperienceCurveRegistry getCurves() { return curves; }
    public Map<String, ScalingFormula> getDefaultStats() { return Map.copyOf(defaultStats); }

    private void loadDefaultStats(Path statsFile) throws IOException {
        if (statsFile == null || !Files.isRegularFile(statsFile)) throw new IOException("Missing class stat defaults: " + statsFile);
        Map<String, Object> root = YamlLite.map(YamlLite.parse(statsFile));
        Object rawDefault = root.get("default");
        if (!(rawDefault instanceof Map<?, ?> rawMap)) throw new IOException("Missing 'default' stat section in " + statsFile);
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            String stat = UtilityMethods.enumName(String.valueOf(entry.getKey()));
            defaultStats.put(stat, ScalingFormula.fromConfig(entry.getValue()));
        }
        if (!defaultStats.containsKey("MAX_HEALTH") || !defaultStats.containsKey("MAX_MANA"))
            throw new IOException("Class stat defaults do not define required max-resource stats");
    }
}
