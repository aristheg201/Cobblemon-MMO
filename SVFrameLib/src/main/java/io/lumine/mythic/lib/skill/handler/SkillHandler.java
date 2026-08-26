package io.lumine.mythic.lib.skill.handler;

import io.lumine.mythic.lib.gui.util.IconOptions;
import io.lumine.mythic.lib.skill.SkillMetadata;
import io.lumine.mythic.lib.skill.parameter.SkillParameter;
import io.lumine.mythic.lib.skill.parameter.value.ScalingFormula;
import io.lumine.mythic.lib.skill.result.SkillResult;
import io.lumine.mythic.lib.skill.trigger.TriggerType;
import io.lumine.mythic.lib.util.configobject.ConfigObject;

import java.text.DecimalFormat;
import java.util.*;

/** Native skill handler base retaining the public 1.7.1 metadata/UI contract. */
public abstract class SkillHandler<T extends SkillResult> {
    private final String id;
    private final IconOptions icon;
    private final Map<String, SkillParameter> params = new LinkedHashMap<>();
    private final Set<String> modifiers = new LinkedHashSet<>();

    public SkillHandler() { this("skill", IconOptions.EMPTY); }
    public SkillHandler(boolean ignored) { this("skill", IconOptions.EMPTY); }
    public SkillHandler(String id) { this(id, IconOptions.EMPTY); }
    public SkillHandler(ConfigObject config) {
        this(config == null || !config.hasKey() ? "skill" : config.getKey(),
                config == null ? IconOptions.EMPTY : IconOptions.from(config.getString("icon", "minecraft:stone")));
    }
    private SkillHandler(String id, IconOptions icon) {
        this.id = id == null ? "skill" : id;
        this.icon = icon == null ? IconOptions.EMPTY : icon;
    }

    public String getId() { return id; }
    public String getLowerCaseId() { return id.toLowerCase(Locale.ROOT); }
    public String getName() { return id; }
    public IconOptions getIcon() { return icon; }
    public List<String> getLore() { return List.of(); }
    public List<String> getCategories() { return List.of(); }
    public TriggerType getDefaultTriggerType() { return TriggerType.CAST; }
    public boolean isTriggerable() { return true; }
    public void addParameter(String id, SkillParameter parameter) { params.put(id, parameter); }
    public String getParameterName(String id) { return id; }
    public double getDefaultItemParameter(String id) { SkillParameter parameter = params.get(id); return parameter == null ? 0 : parameter.getItemDefaultValue(); }
    public ScalingFormula getDefaultFormula(String id) { SkillParameter parameter = params.get(id); return parameter == null ? ScalingFormula.ZERO : parameter.getScalingFormula(); }
    public DecimalFormat getParameterDecimalFormat(String id) { SkillParameter parameter = params.get(id); return parameter == null ? new DecimalFormat("0.##") : parameter.getDecimalFormat(); }
    public void registerModifiers(String... ids) { if (ids != null) modifiers.addAll(Arrays.asList(ids)); }
    public void registerModifiers(Collection<String> ids) { if (ids != null) modifiers.addAll(ids); }
    public Set<String> getParameters() { return Set.copyOf(params.keySet()); }
    public abstract T getResult(SkillMetadata metadata);
    public abstract void whenCast(T result, SkillMetadata metadata);
    public Set<String> getModifiers() { return Set.copyOf(modifiers); }
    public List<String> getUiLore() { return getLore(); }
    @Override public boolean equals(Object object) { return object instanceof SkillHandler<?> handler && id.equalsIgnoreCase(handler.id); }
    @Override public int hashCode() { return id.toLowerCase(Locale.ROOT).hashCode(); }
}
