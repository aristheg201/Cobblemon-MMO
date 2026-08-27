package vn.svframe.svframemmo.experience;

import vn.svframe.svframelib.skill.parameter.value.FormulaFailsafeException;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframemmo.api.player.PlayerData;
import vn.svframe.svframemmo.experience.curve.ExperienceCurve;
import vn.svframe.svframemmo.experience.curve.ExperienceCurveRegistry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Native profession definition ported from SVFrameMMO 1.13.1. */
public final class Profession {
    public enum ProfessionOption {
        EXP_HOLOGRAMS(true);
        private final boolean defaultValue;
        ProfessionOption(boolean defaultValue) { this.defaultValue = defaultValue; }
        public boolean getDefault() { return defaultValue; }
    }

    private final String id;
    private final String name;
    private final int maxLevel;
    private final ExperienceCurve expCurve;
    private final ScalingFormula experience;
    private final String experienceTableId;
    private final List<String> experienceSources;
    private final Map<ProfessionOption, Boolean> options = new EnumMap<>(ProfessionOption.class);
    private final Map<String, Object> raw;

    public Profession(String id, Map<String, Object> config, ExperienceCurveRegistry curves) {
        this.id = normalize(id);
        this.raw = Map.copyOf(Objects.requireNonNull(config, "config"));
        Object rawName = config.get("name");
        if (rawName == null || String.valueOf(rawName).isBlank()) throw new IllegalArgumentException("Could not load profession name: " + id);
        this.name = String.valueOf(rawName);
        this.maxLevel = integer(config.get("max-level"), 0);
        this.expCurve = curves.fromConfig(config.get("exp-curve") == null ? null : String.valueOf(config.get("exp-curve")));
        this.experience = ScalingFormula.fromConfig(config.getOrDefault("experience", 0d));
        this.experienceTableId = config.get("exp-table") == null ? null : String.valueOf(config.get("exp-table"));
        this.experienceSources = stringList(config.get("exp-sources"));

        Map<String, Object> configuredOptions = map(config.get("options"));
        configuredOptions.forEach((key, value) -> {
            try { options.put(ProfessionOption.valueOf(enumName(key)), bool(value, false)); }
            catch (IllegalArgumentException ignored) { }
        });
    }

    public String getId() { return id; }
    public String getKey() { return "profession_" + id; }
    public String getName() { return name; }
    public int getMaxLevel() { return maxLevel; }
    public boolean hasMaxLevel() { return maxLevel > 0; }
    public ExperienceCurve getExpCurve() { return expCurve; }
    public ScalingFormula getExperienceFormula() { return experience; }
    public String getExperienceTableId() { return experienceTableId; }
    public boolean hasExperienceTable() { return experienceTableId != null && !experienceTableId.isBlank(); }
    public List<String> getExperienceSources() { return experienceSources; }
    public Map<String, Object> getRawConfig() { return raw; }
    public boolean getOption(ProfessionOption option) { return options.getOrDefault(option, option.getDefault()); }

    public double getMainExperienceReward(int reachedLevel, PlayerData player) {
        try { return experience.evaluate(reachedLevel, player == null ? null : player.getPlayer()); }
        catch (FormulaFailsafeException exception) {
            exception.log("Could not evaluate profession level-up exp for %s", id);
            return exception.getFailsafe();
        }
    }

    @Override public boolean equals(Object object) { return object instanceof Profession other && id.equals(other.id); }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() { return "Profession{id='" + id + "', name='" + name + "'}"; }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-'); }
    private static String enumName(String value) { return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'); }
    private static int integer(Object value, int fallback) {
        try { return value instanceof Number number ? number.intValue() : value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            ArrayList<String> result = new ArrayList<>(list.size());
            for (Object element : list) if (element != null) result.add(String.valueOf(element));
            return List.copyOf(result);
        }
        if (value == null || value instanceof Map<?, ?>) return List.of();
        return List.of(String.valueOf(value));
    }
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, element) -> result.put(String.valueOf(key), element));
        return result;
    }
}
