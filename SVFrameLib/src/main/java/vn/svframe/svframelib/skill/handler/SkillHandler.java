package vn.svframe.svframelib.skill.handler;

import vn.svframe.svframelib.gui.util.IconOptions;
import vn.svframe.svframelib.skill.SkillMetadata;
import vn.svframe.svframelib.skill.parameter.SkillParameter;
import vn.svframe.svframelib.skill.parameter.value.ScalingFormula;
import vn.svframe.svframelib.skill.result.SkillResult;
import vn.svframe.svframelib.skill.trigger.TriggerType;
import vn.svframe.svframelib.util.configobject.ConfigObject;
import vn.svframe.svframelib.util.configobject.MapConfigObject;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Native skill handler base retaining the public MythicLib 1.7.1 metadata/UI contract. */
public abstract class SkillHandler<T extends SkillResult> {
    private static final List<String> BASE_MODIFIERS = List.of("cooldown", "mana", "stamina", "timer", "delay");

    private final String id;
    private final String name;
    private final boolean triggerable;
    private final IconOptions icon;
    private final List<String> lore;
    private final List<String> categories;
    private final TriggerType defaultTriggerType;
    private final Map<String, SkillParameter> params = new LinkedHashMap<>();
    private final Set<String> modifiers = new LinkedHashSet<>();

    public SkillHandler() { this(null, null); }
    public SkillHandler(boolean ignored) { this(null, null); }
    public SkillHandler(String id) { this(id, null); }
    public SkillHandler(ConfigObject config) { this(config == null ? null : config.getKey(), config); }

    protected SkillHandler(String requestedId, ConfigObject config) {
        BuiltinSkillHandler builtin = getClass().getAnnotation(BuiltinSkillHandler.class);
        String inferred = requestedId;
        if (inferred == null || inferred.isBlank()) {
            inferred = config != null && config.hasKey() ? config.getKey() : getClass().getSimpleName();
        }
        this.id = enumName(inferred);
        this.triggerable = builtin == null || builtin.triggerable();

        LinkedHashSet<String> parameterIds = new LinkedHashSet<>(BASE_MODIFIERS);
        if (builtin != null) parameterIds.addAll(Arrays.asList(builtin.mods()));
        else parameterIds.addAll(parameterKeys(config));
        for (String parameter : parameterIds) initializeModifier(parameter, config);

        this.name = config == null ? inferSkillName(id) : config.getString("name", inferSkillName(id));
        this.icon = config == null ? IconOptions.EMPTY : IconOptions.from(raw(config, "icon"));
        this.lore = List.copyOf(stringList(raw(config, "lore")));

        TriggerType trigger = TriggerType.CAST;
        if (!triggerable) trigger = TriggerType.API;
        else if (config != null && config.contains("trigger")) {
            try { trigger = TriggerType.valueOf(config.getString("trigger", "CAST")); }
            catch (IllegalArgumentException ignored) { trigger = TriggerType.CAST; }
        }
        this.defaultTriggerType = trigger;

        ArrayList<String> configuredCategories = new ArrayList<>(stringList(raw(config, "categories")));
        if (!configuredCategories.contains(id)) configuredCategories.add(id);
        String activity = trigger.isPassive() ? "PASSIVE" : "ACTIVE";
        if (!configuredCategories.contains(activity)) configuredCategories.add(activity);
        this.categories = List.copyOf(configuredCategories);
    }

    public String getId() { return id; }
    public String getLowerCaseId() { return id.toLowerCase(Locale.ROOT); }
    public String getName() { return name; }
    public IconOptions getIcon() { return icon; }
    public List<String> getLore() { return lore; }
    public List<String> getCategories() { return categories; }
    public TriggerType getDefaultTriggerType() { return defaultTriggerType; }
    public boolean isTriggerable() { return triggerable; }

    public void addParameter(String id, SkillParameter parameter) {
        params.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(parameter, "parameter"));
    }

    public String getParameterName(String id) {
        SkillParameter parameter = params.get(id);
        return parameter == null ? id : parameter.getTranslate();
    }

    public double getDefaultItemParameter(String id) {
        SkillParameter parameter = params.get(id);
        return parameter == null ? 0d : parameter.getItemDefaultValue();
    }

    public ScalingFormula getDefaultFormula(String id) {
        SkillParameter parameter = params.get(id);
        return parameter == null ? ScalingFormula.ZERO : parameter.getScalingFormula();
    }

    public DecimalFormat getParameterDecimalFormat(String id) {
        SkillParameter parameter = params.get(id);
        return parameter == null ? SkillParameter.DEFAULT_DECIMAL_FORMAT : parameter.getDecimalFormat();
    }

    /** Original 1.7.1 behavior: registering a modifier also creates an empty parameter descriptor. */
    public void registerModifiers(String... ids) {
        if (ids == null) return;
        for (String id : ids) {
            if (id == null || id.isBlank()) continue;
            modifiers.add(id);
            params.put(id, SkillParameter.empty(id));
        }
    }

    public void registerModifiers(Collection<String> ids) {
        if (ids != null) registerModifiers(ids.toArray(String[]::new));
    }

    public Set<String> getParameters() { return Set.copyOf(params.keySet()); }
    public abstract T getResult(SkillMetadata metadata);
    public abstract void whenCast(T result, SkillMetadata metadata);
    public Set<String> getModifiers() { return Set.copyOf(modifiers); }
    public List<String> getUiLore() { return getLore(); }

    private void initializeModifier(String parameter, ConfigObject config) {
        if (parameter == null || parameter.isBlank()) return;
        Object input = parameterValue(config, parameter);
        params.computeIfAbsent(parameter, key -> SkillParameter.fromConfig(input, key));
        modifiers.add(parameter);
    }

    private static Set<String> parameterKeys(ConfigObject config) {
        if (config == null || !config.contains("parameters")) return Set.of();
        try { return config.getObject("parameters").getKeys(); }
        catch (RuntimeException ignored) { return Set.of(); }
    }

    private static Object parameterValue(ConfigObject config, String parameter) {
        if (config == null || !config.contains("parameters")) return null;
        try {
            ConfigObject parameters = config.getObject("parameters");
            if (!parameters.contains(parameter)) return null;
            return raw(parameters, parameter);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object raw(ConfigObject config, String key) {
        if (config == null || !config.contains(key)) return null;
        if (config instanceof MapConfigObject map) return map.asMap().get(key);
        try { return config.getObject(key); }
        catch (RuntimeException ignored) { return config.getString(key, null); }
    }

    private static List<String> stringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof Collection<?> collection) {
            ArrayList<String> result = new ArrayList<>(collection.size());
            for (Object element : collection) result.add(String.valueOf(element));
            return result;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) return List.of();
        return text.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
    }

    private static String enumName(String value) {
        return Objects.requireNonNullElse(value, "SKILL").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String inferSkillName(String value) {
        String normalized = Objects.requireNonNullElse(value, "SKILL").replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder output = new StringBuilder();
        for (String word : normalized.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (!output.isEmpty()) output.append(' ');
            output.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return output.toString();
    }

    @Override public boolean equals(Object object) { return object instanceof SkillHandler<?> handler && id.equalsIgnoreCase(handler.id); }
    @Override public int hashCode() { return id.toLowerCase(Locale.ROOT).hashCode(); }
}
